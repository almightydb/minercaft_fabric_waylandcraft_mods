use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use pipewire as pw;
use pw::prelude::*;

static CAPTURE_FRAMES: std::sync::Mutex<Option<Arc<Mutex<Option<FrameData>>>>> =
    std::sync::Mutex::new(None);

#[derive(Clone)]
pub struct FrameData {
    pub data: Vec<u8>,
    pub width: u32,
    pub height: u32,
}

/// 调用 gdbus call 并返回 stdout
fn gdbus_call(args: &[&str]) -> Result<String, String> {
    let output = std::process::Command::new("gdbus")
        .args(args)
        .env("DBUS_SESSION_BUS_ADDRESS",
             std::env::var("DBUS_SESSION_BUS_ADDRESS")
                 .unwrap_or_else(|_| format!("unix:path=/run/user/{}/bus", unsafe { libc::getuid() })))
        .output()
        .map_err(|e| format!("gdbus: {}", e))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("gdbus failed: {}", stderr.trim()));
    }

    Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
}

/// 等待 D-Bus Response 信号（通过 gdbus monitor）
fn wait_portal_response(request_path: &str, timeout_secs: u64) -> Result<(u32, String), String> {
    // gdbus monitor 会监听信号，我们用 timeout 包装
    let output = std::process::Command::new("timeout")
        .args(&[
            &timeout_secs.to_string(),
            "gdbus", "monitor", "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", request_path,
        ])
        .env("DBUS_SESSION_BUS_ADDRESS",
             std::env::var("DBUS_SESSION_BUS_ADDRESS")
                 .unwrap_or_else(|_| format!("unix:path=/run/user/{}/bus", unsafe { libc::getuid() })))
        .output()
        .map_err(|e| format!("monitor: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);

    // 解析: Response(uint32 0, @a{sv} {...})
    for line in stdout.lines() {
        if line.contains("Response") {
            // 提取 code (第一个数字)
            let code = line
                .split(|c: char| !c.is_ascii_digit())
                .find(|s| !s.is_empty())
                .and_then(|s| s.parse::<u32>().ok())
                .unwrap_or(0);

            return Ok((code, line.to_string()));
        }
    }

    Err(format!("no Response signal received (timeout={}s)", timeout_secs))
}

/// 通过 XDG Desktop Portal ScreenCast 启动捕获
/// 返回 PipeWire 节点 ID
pub fn start_portal_capture() -> Result<u32, String> {
    // 1. CreateSession
    eprintln!("[portal] CreateSession...");
    let out = gdbus_call(&[
        "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.ScreenCast.CreateSession",
        "{'session_handle_token': <'wcs1'>, 'handle_token': <'wcr1'>}",
    ])?;
    // 返回: (objectpath '/org/.../wcr1',)
    let req1 = extract_object_path(&out)?;
    eprintln!("[portal] CreateSession req: {}", req1);

    let (code, _) = wait_portal_response(&req1, 10)?;
    if code != 0 { return Err(format!("CreateSession failed: {}", code)); }
    // session_handle 从 Response 中提取 (下次用 gdbus call 的 async 版本)
    // 暂时用固定路径模式
    let session = req1.replace("/request/", "/session/").replace("wcr1", "wcs1");
    eprintln!("[portal] Session: {}", session);

    // 2. SelectSources
    eprintln!("[portal] SelectSources...");
    let out = gdbus_call(&[
        "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.ScreenCast.SelectSources",
        &session,
        "{'handle_token': <'wcr2'>, 'types': <uint32 2>, 'multiple': <false>}",
    ])?;
    let req2 = extract_object_path(&out)?;
    let (code, _) = wait_portal_response(&req2, 10)?;
    if code != 0 { return Err(format!("SelectSources failed: {}", code)); }

    // 3. Start (用户需要在弹窗中确认)
    eprintln!("[portal] Start (请在弹窗中选择窗口并点击分享)...");
    let out = gdbus_call(&[
        "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.ScreenCast.Start",
        &session,
        "''",
        "{'handle_token': <'wcr3'>}",
    ])?;
    let req3 = extract_object_path(&out)?;
    eprintln!("[portal] Start req: {}", req3);

    let (code, response) = wait_portal_response(&req3, 60)?;
    if code != 0 { return Err(format!("Start failed: {}", code)); }

    // 4. 从 Response 中提取 PipeWire 节点 ID
    let node_id = extract_node_id_from_response(&response)?;
    eprintln!("[portal] PipeWire node: {}", node_id);

    Ok(node_id)
}

fn extract_object_path(s: &str) -> Result<String, String> {
    // 格式: (objectpath '/org/freedesktop/portal/desktop/request/1_109/wr1',)
    let start = s.find('\'').ok_or("no quote")?;
    let rest = &s[start + 1..];
    let end = rest.find('\'').ok_or("no closing quote")?;
    Ok(rest[..end].to_string())
}

fn extract_node_id_from_response(response: &str) -> Result<u32, String> {
    // Response 格式: Response(uint32 0, @a{sv} {...streams: [(uint32 NODE_ID, {...})]...})
    // 查找 streams 后面的第一个数字
    if let Some(pos) = response.find("streams") {
        let rest = &response[pos..];
        // 找到第一个 uint32 或数字
        for part in rest.split(|c: char| !c.is_ascii_digit()) {
            if !part.is_empty() {
                if let Ok(id) = part.parse::<u32>() {
                    if id > 100 { // 排除小数字 (code 等)
                        return Ok(id);
                    }
                }
            }
        }
    }

    // 备选：查找所有大数字
    for part in response.split(|c: char| !c.is_ascii_digit()) {
        if !part.is_empty() {
            if let Ok(id) = part.parse::<u32>() {
                if id > 1000 {
                    return Ok(id);
                }
            }
        }
    }

    Err(format!("cannot extract node ID from: {}", &response[..response.len().min(500)]))
}

/// 连接 PipeWire 节点并读取帧
pub fn start_pw_stream(node_id: u32) -> Result<(), String> {
    let frame_data = Arc::new(Mutex::new(None));
    {
        let mut guard = CAPTURE_FRAMES.lock().map_err(|e| format!("lock: {}", e))?;
        *guard = Some(frame_data.clone());
    }

    std::thread::Builder::new()
        .name("pw-stream".to_string())
        .spawn(move || {
            if let Err(e) = pw_stream_loop(node_id, frame_data) {
                eprintln!("[portal] PW error: {}", e);
            }
        })
        .map_err(|e| format!("spawn: {}", e))?;

    Ok(())
}

fn pw_stream_loop(node_id: u32, frame_data: Arc<Mutex<Option<FrameData>>>) -> Result<(), String> {
    pw::init();

    let mainloop = pw::main_loop::MainLoop::new(None).map_err(|e| format!("mainloop: {}", e))?;
    let context = pw::context::Context::new(&mainloop).map_err(|e| format!("context: {}", e))?;
    let core = context.connect(None).map_err(|e| format!("connect: {}", e))?;

    let stream = pw::stream::Stream::new(
        &core,
        "wc-capture",
        pw::properties::properties! {
            *pw::keys::MEDIA_TYPE => "Video",
            *pw::keys::MEDIA_CATEGORY => "Capture",
            *pw::keys::MEDIA_ROLE => "Screen",
        },
    ).map_err(|e| format!("stream: {}", e))?;

    let frame_ref = frame_data;
    let mut video_info: pw::spa::param::video::VideoInfoRaw = Default::default();

    let _listener = stream
        .add_local_listener_with_user_data(&mut video_info)
        .state_changed(|_, _, old, new| {
            eprintln!("[portal] PW state: {:?} -> {:?}", old, new);
        })
        .param_changed(|_stream, user_data, _id, param| {
            let Some(param) = param else { return; };
            if let Ok((media_type, media_subtype)) = pw::spa::param::format_utils::parse_format(param) {
                if media_type == pw::spa::param::format::MediaType::Video
                    && media_subtype == pw::spa::param::format::MediaSubtype::Raw
                {
                    user_data.parse(param).expect("parse video format");
                    eprintln!("[portal] Video: {}x{}", user_data.size().width, user_data.size().height);
                }
            }
        })
        .process(move |stream, user_data| {
            if let Some(mut buffer) = stream.dequeue_buffer() {
                let datas = buffer.datas_mut();
                if !datas.is_empty() {
                    let data = &mut datas[0];
                    let size = data.chunk().size() as usize;
                    if size > 0 {
                        if let Some(slice) = data.data() {
                            let w = user_data.size().width;
                            let h = user_data.size().height;
                            let mut frame = frame_ref.lock().unwrap();
                            *frame = Some(FrameData {
                                data: slice[..size.min(slice.len())].to_vec(),
                                width: w,
                                height: h,
                            });
                        }
                    }
                }
            }
        })
        .register()
        .map_err(|e| format!("register: {}", e))?;

    let obj = pw::spa::pod::object!(
        pw::spa::utils::SpaTypes::ObjectParamFormat,
        pw::spa::param::ParamType::EnumFormat,
        pw::spa::pod::property!(
            pw::spa::param::format::FormatProperties::MediaType,
            Id,
            pw::spa::param::format::MediaType::Video
        ),
        pw::spa::pod::property!(
            pw::spa::param::format::FormatProperties::MediaSubtype,
            Id,
            pw::spa::param::format::MediaSubtype::Raw
        ),
        pw::spa::pod::property!(
            pw::spa::param::format::FormatProperties::VideoFormat,
            Choice,
            Enum,
            Id,
            pw::spa::param::video::VideoFormat::BGRx,
            pw::spa::param::video::VideoFormat::BGRx,
            pw::spa::param::video::VideoFormat::BGRA,
            pw::spa::param::video::VideoFormat::RGBA,
        ),
        pw::spa::pod::property!(
            pw::spa::param::format::FormatProperties::VideoSize,
            Choice,
            Range,
            Rectangle,
            pw::spa::utils::Rectangle { width: 1920, height: 1080 },
            pw::spa::utils::Rectangle { width: 1, height: 1 },
            pw::spa::utils::Rectangle { width: 7680, height: 4320 }
        ),
        pw::spa::pod::property!(
            pw::spa::param::format::FormatProperties::VideoFramerate,
            Choice,
            Range,
            Fraction,
            pw::spa::utils::Fraction { num: 30, denom: 1 },
            pw::spa::utils::Fraction { num: 0, denom: 1 },
            pw::spa::utils::Fraction { num: 120, denom: 1 }
        ),
    );

    let values: Vec<u8> = pw::spa::pod::serialize::PodSerializer::serialize(
        std::io::Cursor::new(Vec::new()),
        &pw::spa::pod::Value::Object(obj),
    )
    .map_err(|e| format!("serialize: {}", e))?
    .0
    .into_inner();

    let mut params = [pw::spa::pod::Pod::from_bytes(&values).ok_or("pod from bytes")?];

    stream.connect(
        pw::spa::utils::Direction::Input,
        Some(node_id),
        pw::stream::StreamFlags::AUTOCONNECT | pw::stream::StreamFlags::MAP_BUFFERS,
        &mut params,
    ).map_err(|e| format!("connect: {}", e))?;

    eprintln!("[portal] PW stream connected to node {}", node_id);
    mainloop.run();

    Ok(())
}

pub fn get_frame() -> Option<FrameData> {
    let guard = CAPTURE_FRAMES.lock().ok()?;
    guard.as_ref().and_then(|arc| arc.lock().ok()?.clone())
}
