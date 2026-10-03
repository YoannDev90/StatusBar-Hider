use std::io::{BufRead, BufReader};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::Sender;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use anyhow::{bail, Context, Result};

use crate::parse::{parse_threadtime, Record};

/// Shared handle on the running `adb logcat` child so the main thread can kill it.
pub struct Logcat {
	child: Mutex<Child>,
}

impl Logcat {
	pub fn kill(&self) {
		if let Ok(mut c) = self.child.lock() {
			let _ = c.kill();
			let _ = c.wait();
		}
	}
}

pub fn adb_base(serial: Option<&str>) -> Command {
	let mut c = Command::new("adb");
	if let Some(s) = serial {
		c.args(["-s", s]);
	}
	c
}

/// Pick a serial: explicit flag, or the single connected device.
pub fn pick_serial(explicit: Option<&str>) -> Result<String> {
	if let Some(s) = explicit {
		return Ok(s.to_string());
	}
	let out = Command::new("adb")
		.args(["devices"])
		.output()
		.context("adb not found in PATH — install platform-tools")?;
	let mut devices = Vec::new();
	for line in String::from_utf8_lossy(&out.stdout).lines().skip(1) {
		let mut it = line.split_whitespace();
		if let (Some(id), Some(state)) = (it.next(), it.next()) {
			if state == "device" {
				devices.push(id.to_string());
			}
		}
	}
	match devices.len() {
		0 => bail!("no adb device connected (or unauthorized)"),
		1 => Ok(devices.remove(0)),
		_ => bail!("multiple devices connected — pass --serial (found: {})", devices.join(", ")),
	}
}

/// Resolve the actual package name: try `pkg`, then `pkg.debug`.
pub fn resolve_package(serial: &str, pkg: &str) -> Result<String> {
	for candidate in [pkg.to_string(), format!("{pkg}.debug")] {
		let out = adb_base(Some(serial))
			.args(["shell", "pm", "path", &candidate])
			.output();
		if let Ok(o) = out {
			let stdout = String::from_utf8_lossy(&o.stdout);
			if stdout.contains("package:") {
				return Ok(candidate);
			}
		}
	}
	bail!("package {pkg} not installed on device")
}

/// Pid of `package` via `pidof` (fast, single process — app has no remote process).
pub fn pid_of(serial: &str, package: &str) -> Option<u32> {
	let out = adb_base(Some(serial))
		.args(["shell", "pidof", "-s", package])
		.output()
		.ok()?;
	if !out.status.success() {
		return None;
	}
	String::from_utf8_lossy(&out.stdout)
		.split_whitespace()
		.next()
		.and_then(|s| s.parse().ok())
}

/// Start `adb logcat` streaming into `tx`.
///
/// * `-T 1` — only the newest line of history, then follow.
/// * `-b main,system,crash` — crash buffer included (crashes live there).
/// * threadtime format parsed by [`parse_threadtime`]; continuation lines
///   (stack traces) are merged into the previous record here.
pub fn spawn_logcat(serial: &str, clear: bool, tx: Sender<Event>) -> Result<Arc<Logcat>> {
	if clear {
		let _ = adb_base(Some(serial)).args(["logcat", "-c"]).output();
	}
	let mut child = adb_base(Some(serial))
		.args(["logcat", "-v", "threadtime", "-b", "main,system,crash", "-T", "1"])
		.stdout(Stdio::piped())
		.stderr(Stdio::null())
		.spawn()
		.context("failed to spawn adb logcat")?;

	let stdout = child.stdout.take().context("no stdout")?;
	let handle = Arc::new(Logcat {
		child: Mutex::new(child),
	});

	let lc = Arc::clone(&handle);
	std::thread::spawn(move || {
		let mut last: Option<Record> = None;
		for line in BufReader::new(stdout).lines() {
			let Ok(line) = line else { break };
			match parse_threadtime(&line) {
				Ok(Some(rec)) => {
					if let Some(prev) = last.replace(rec) {
						if tx.send(Event::Record(prev)).is_err() {
							break;
						}
					}
				}
				Ok(None) => {
					// continuation (stacktrace) or banner line → append to prev
					if !line.starts_with("---------") {
						if let Some(prev) = last.as_mut() {
							prev.msg.push('\n');
							prev.msg.push_str(&line);
						}
					}
				}
				Err(e) => {
					let _ = tx.send(Event::ParseError(e.to_string()));
				}
			}
		}
		if let Some(prev) = last {
			let _ = tx.send(Event::Record(prev));
		}
		lc.kill();
		let _ = tx.send(Event::LogcatExited);
	});

	Ok(handle)
}

/// Poll the app pid every `every` ms; send [`Event::PidChanged`] on change.
pub fn spawn_pid_watcher(
	serial: String,
	package: String,
	tx: Sender<Event>,
	every: Duration,
) -> Arc<Mutex<Option<u32>>> {
	let current = Arc::new(Mutex::new(None::<u32>));
	let out_slot = Arc::clone(&current);
	std::thread::spawn(move || {
		loop {
			let pid = pid_of(&serial, &package);
			let mut guard = out_slot.lock().unwrap();
			if *guard != pid {
				*guard = pid;
				if tx.send(Event::PidChanged(pid)).is_err() {
					break;
				}
			}
			drop(guard);
			std::thread::sleep(every);
		}
	});
	current
}

pub enum Event {
	Record(Record),
	PidChanged(Option<u32>),
	LogcatExited,
	/// A line failed to parse (malformed logcat output); string is the reason.
	ParseError(String),
}

#[cfg(test)]
mod tests {
	use super::*;

	#[test]
	fn pick_serial_fails_gracefully_without_adb() {
		// Can't assert success (depends on env); just ensure it doesn't panic on parse.
		if Command::new("adb").arg("--version").output().is_err() {
			assert!(pick_serial(None).is_err());
		}
	}
}
