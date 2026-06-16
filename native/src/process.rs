use std::ffi::OsString;
use std::process::{Command, Stdio};

pub fn spawn(
    cmd: String,
    args: Vec<String>,
    env: Vec<(OsString, OsString)>,
) -> Result<(), ()> {
    let mut command = Command::new(cmd);
    command
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    // Remove evil environment variables of the devil
    command
        .env_remove("DISPLAY")
        .env_remove("WAYLAND_DISPLAY")
        .env_remove("LD_LIBRARY_PATH");

    // Set XDG_RUNTIME_DIR if not already set (needed for Wayland socket discovery)
    if std::env::var("XDG_RUNTIME_DIR").is_err() {
        if let Some(uid) = get_uid() {
            let runtime_dir = format!("/run/user/{}", uid);
            command.env("XDG_RUNTIME_DIR", &runtime_dir);
        }
    }

    command.envs(env);

    // Double-fork to run the executable.
    // Has to do with preventing zombie processes and such
    match unsafe { libc::fork() } {
        0 => {
            // child process
            unsafe {
                libc::setsid();
            }
            let _ = command.spawn();
            unsafe {
                libc::_exit(0);
            }
        }
        -1 => {
            // fork failed
            return Err(());
        }
        _ => { // parent process
        }
    }

    unsafe {
        libc::wait(std::ptr::null_mut());
    }

    Ok(())
}

fn get_uid() -> Option<u32> {
    unsafe { Some(libc::getuid()) }
}
