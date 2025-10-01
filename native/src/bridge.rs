use std::ops::DerefMut;
use crate::{WaylandCraft, wlc_init};
use smithay::{
    wayland::{
        shell::xdg::ToplevelSurface,
        compositor::{
            SurfaceAttributes, BufferAssignment, with_states
        },
        shm::with_buffer_contents,
    },
    reexports::{
        wayland_server::{
            protocol::{
                wl_surface::WlSurface,
            },
        },
    },
};
use jni::{
    objects::{JClass, JObject},
    sys::{jlong, jstring, jarray, jsize, jint, jvalue},
    signature::{ReturnType, Primitive},
    JNIEnv,
};

pub(crate) struct BridgeState {
    toplevels: Vec<Box<ToplevelSurface>>,
    surfaces: Vec<Box<WlSurface>>,
}

impl BridgeState {
    pub fn new() -> Self {
        BridgeState {
            toplevels: vec![],
            surfaces: vec![],
        }
    }
}

fn jptr_to_instance(ptr: jlong) -> &'static mut WaylandCraft<'static> {
    let ptr: *mut WaylandCraft = (ptr as usize) as *mut WaylandCraft;
    unsafe { &mut *ptr }
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_init<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>
) -> jlong {
    let instance = match wlc_init() {
        Ok(i) => i,
        Err(err) => {
            let _ = env.throw_new(
                "java/lang/RuntimeException",
                err.to_string()
            );
            return 0;
        }
    };

    let instance_box: Box<WaylandCraft> = Box::new(instance);
    let ptr: *mut WaylandCraft = Box::into_raw(instance_box);
    let addr: u64 = ptr.addr() as u64;
    addr as i64
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_update<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    ptr: jlong
) {
    let instance = jptr_to_instance(ptr);
    instance.update();
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_socket<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    ptr: jlong
) -> jstring {
    let instance = jptr_to_instance(ptr);
    let socket = instance.state.socket.to_str().unwrap();
    env.new_string(socket).unwrap().into_raw()
}

// Get or insert an element and return its handle
fn insert_get_handle<T>(vec: &mut Vec<Box<T>>, elem: &T) -> jlong
    where T: Clone + PartialEq
{
    if !vec.iter().any(|b| **b == *elem) {
        vec.push(Box::new(elem.clone()));
    }

    let ptr: &mut T = vec
        .iter_mut()
        .find(|r| ***r == *elem)
        .unwrap();
    ((ptr as *mut T) as usize) as jlong
}

// Insert all elements that aren't in the list already
fn insert_all<T>(vec: &mut Vec<Box<T>>, elems: &[T])
    where T: Clone + PartialEq
{
    for elem in elems {
        if !vec.iter().any(|b| **b == *elem) {
            vec.push(Box::new(elem.clone()));
        }
    }
}

// Get handles of all elements in the list
fn get_all_handles<T>(vec: &mut Vec<Box<T>>) -> Vec<jlong>
    where T: Clone + PartialEq
{
    vec
        .iter_mut()
        .map(|r| ((&mut **r) as *mut T) as usize as jlong)
        .collect()
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_toplevels<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    ptr: jlong
) -> jarray {
    let instance = jptr_to_instance(ptr);

    insert_all(
        &mut instance.bridge.toplevels,
        instance.state.xdg_state.toplevel_surfaces()
    );

    instance.bridge.toplevels.retain(|t| t.alive());

    let toplevels = get_all_handles(&mut instance.bridge.toplevels);
    let array = env.new_long_array(toplevels.len() as jsize).unwrap();
    env.set_long_array_region(&array, 0, &toplevels).unwrap();
    array.into_raw()
}

#[allow(non_upper_case_globals)]
const WLCSurface_class: &str = "dev/evvie/waylandcraft/bridge/WLCSurface";

fn jptr_to_wlsurface(ptr: jlong) -> &'static mut WlSurface {
    let ptr: *mut WlSurface = (ptr as usize) as *mut WlSurface;
    unsafe { &mut *ptr }
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_updateSurface<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    obj: JObject<'l>
) {
    let handle: jlong = env.get_field_unchecked(
        &obj,
        (WLCSurface_class, "handle", "J"),
        ReturnType::Primitive(Primitive::Long)
    ).unwrap().j().unwrap();

    let surface = jptr_to_wlsurface(handle);
    with_states(surface, |data| {
        let mut attr_guard = data
            .cached_state
            .get::<SurfaceAttributes>();
        let attr = attr_guard
            .deref_mut()
            .current();

        let maybe_buf = if let Some(assign) = &attr.buffer {
            match assign {
                BufferAssignment::NewBuffer(b) => Some(b),
                BufferAssignment::Removed => None,
            }
        } else {
            None
        };
        if let Some(buf) = maybe_buf {
            let _ = with_buffer_contents(buf, |ptr, _len, metadata| {
                let width = metadata.width as jint;
                let height = metadata.height as jint;
                unsafe {
                    let ptr = ptr.offset(metadata.offset as isize);
                    let jptr = (ptr as usize) as jlong;
                    let sig = "(JII)V";
                    env.call_method_unchecked(
                        &obj,
                        (WLCSurface_class, "attachShmBuffer", sig),
                        ReturnType::Primitive(Primitive::Void),
                        &[
                            jvalue { j: jptr },
                            jvalue { i: width },
                            jvalue { i: height },
                        ]
                    ).unwrap();
                }
            });
            buf.release();
            attr.buffer = None;
        }
    });
}

fn jptr_to_toplevel(ptr: jlong) -> &'static mut ToplevelSurface {
    let ptr: *mut ToplevelSurface = (ptr as usize) as *mut ToplevelSurface;
    unsafe { &mut *ptr }
}

#[unsafe(no_mangle)]
pub extern "system"
fn Java_dev_evvie_waylandcraft_bridge_WaylandCraftBridge_toplevelSurface<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    ptr: jlong,
    handle: jlong
) -> jlong {
    let instance = jptr_to_instance(ptr);
    let toplevel: &mut ToplevelSurface = jptr_to_toplevel(handle);
    let surface: &WlSurface = toplevel.wl_surface();

    insert_get_handle(&mut instance.bridge.surfaces, surface)
}
