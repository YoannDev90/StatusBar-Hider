use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, serde::Serialize)]
pub enum Level {
	Verbose,
	Debug,
	Info,
	Warn,
	Error,
	Fatal,
}

impl Level {
	pub fn from_logcat(c: char) -> Option<Self> {
		match c {
			'V' => Some(Self::Verbose),
			'D' => Some(Self::Debug),
			'I' => Some(Self::Info),
			'W' => Some(Self::Warn),
			'E' => Some(Self::Error),
			'F' => Some(Self::Fatal),
			_ => None,
		}
	}

	pub fn from_str(s: &str) -> Option<Self> {
		match s.trim().to_ascii_lowercase().as_str() {
			"v" | "verbose" => Some(Self::Verbose),
			"d" | "debug" => Some(Self::Debug),
			"i" | "info" => Some(Self::Info),
			"w" | "warn" | "warning" => Some(Self::Warn),
			"e" | "error" => Some(Self::Error),
			"f" | "fatal" => Some(Self::Fatal),
			_ => None,
		}
	}

	pub fn as_char(self) -> char {
		match self {
			Self::Verbose => 'V',
			Self::Debug => 'D',
			Self::Info => 'I',
			Self::Warn => 'W',
			Self::Error => 'E',
			Self::Fatal => 'F',
		}
	}

	pub fn name(self) -> &'static str {
		match self {
			Self::Verbose => "verbose",
			Self::Debug => "debug",
			Self::Info => "info",
			Self::Warn => "warn",
			Self::Error => "error",
			Self::Fatal => "fatal",
		}
	}
}

#[derive(Debug, Clone)]
pub struct Record {
	/// Epoch milliseconds (best effort: parsed from logcat date without year,
	/// completed with the current year, corrected across year boundaries).
	pub epoch_ms: u64,
	/// `HH:MM:SS.mmm` as printed by logcat (already zero-padded).
	pub time: String,
	pub pid: u32,
	pub tid: u32,
	pub level: Level,
	pub tag: String,
	pub msg: String,
}

impl Record {
	pub fn iso_time(&self) -> String {
		let secs = (self.epoch_ms / 1000) as i64;
		let ms = self.epoch_ms % 1000;
		let (y, mo, d, h, mi, s) = civil_from_unix(secs);
		format!("{y:04}-{mo:02}-{d:02}T{h:02}:{mi:02}:{s:02}.{ms:03}")
	}
}

/// Parse one `threadtime` line. Continuation lines (stack traces) return
/// `Ok(None)` — the caller attaches them to the previous record.
///
/// Format: `MM-DD HH:MM:SS.mmm   PID   TID LEVEL TAG     : message`
pub fn parse_threadtime(line: &str) -> anyhow::Result<Option<Record>> {
	let line = line.trim_end_matches(['\r', '\n']);
	if line.is_empty() || line.starts_with("---------") {
		return Ok(None);
	}
	if line.len() < 20 || line.as_bytes()[2] != b'-' {
		return Ok(None); // continuation or garbage
	}

	let (date, p0) = take_token(line, 0).ok_or_else(|| anyhow::anyhow!("no date"))?;
	let (time, p1) = take_token(line, p0).ok_or_else(|| anyhow::anyhow!("no time"))?;
	let (pid_s, p2) = take_token(line, p1).ok_or_else(|| anyhow::anyhow!("no pid"))?;
	let (tid_s, p3) = take_token(line, p2).ok_or_else(|| anyhow::anyhow!("no tid"))?;
	let (level_s, p4) = take_token(line, p3).ok_or_else(|| anyhow::anyhow!("no level"))?;
	let rest = line[p4..].trim_start();

	let pid: u32 = pid_s.parse().map_err(|_| anyhow::anyhow!("bad pid"))?;
	let tid: u32 = tid_s.parse().map_err(|_| anyhow::anyhow!("bad tid"))?;
	let level_c = level_s.chars().next().ok_or_else(|| anyhow::anyhow!("no level"))?;

	let level = Level::from_logcat(level_c).ok_or_else(|| anyhow::anyhow!("bad level {level_c}"))?;

	// TAG is padded with spaces, then `: message`
	let (tag, msg) = match rest.find(':') {
		Some(idx) => (rest[..idx].trim_end(), rest[idx + 1..].trim_start()),
		None => (rest.trim_end(), ""),
	};

	// Split `MM-DD` / `HH:MM:SS.mmm`
	let (month, day) = parse_md(date)?;
	let (hour, min, sec, ms) = parse_time(time)?;
	let epoch_ms = stamp_to_epoch(month, day, hour, min, sec, ms)?;

	Ok(Some(Record {
		epoch_ms,
		time: time.to_string(),
		pid,
		tid,
		level,
		tag: tag.to_string(),
		msg: msg.to_string(),
	}))
}

/// Take one whitespace-delimited token starting at `from`: (token, end_offset).
fn take_token(s: &str, from: usize) -> Option<(&str, usize)> {
	let bytes = s.as_bytes();
	let mut i = from;
	while i < bytes.len() && bytes[i].is_ascii_whitespace() {
		i += 1;
	}
	if i >= bytes.len() {
		return None;
	}
	let start = i;
	while i < bytes.len() && !bytes[i].is_ascii_whitespace() {
		i += 1;
	}
	Some((&s[start..i], i))
}

fn parse_md(s: &str) -> anyhow::Result<(u32, u32)> {
	let mut it = s.split('-');
	let m: u32 = it.next().and_then(|x| x.parse().ok()).ok_or_else(|| anyhow::anyhow!("bad month"))?;
	let d: u32 = it.next().and_then(|x| x.parse().ok()).ok_or_else(|| anyhow::anyhow!("bad day"))?;
	Ok((m, d))
}

fn parse_time(s: &str) -> anyhow::Result<(u32, u32, u32, u32)> {
	// HH:MM:SS.mmm
	let (hms, frac) = s.split_once('.').unwrap_or((s, "0"));
	let mut it = hms.split(':');
	let h: u32 = it.next().and_then(|x| x.parse().ok()).ok_or_else(|| anyhow::anyhow!("bad hour"))?;
	let m: u32 = it.next().and_then(|x| x.parse().ok()).ok_or_else(|| anyhow::anyhow!("bad min"))?;
	let sec: u32 = it.next().and_then(|x| x.parse().ok()).ok_or_else(|| anyhow::anyhow!("bad sec"))?;
	// `.5` → 500ms, `.12` → 120ms, `.123` → 123ms
	let mut frac = frac.to_string();
	while frac.len() < 3 {
		frac.push('0');
	}
	let ms: u32 = frac[..3].parse().unwrap_or(0);
	Ok((h, m, sec, ms))
}

/// Build epoch ms from month/day/time, choosing the year that makes the
/// timestamp closest to *now* (handles Dec→Jan wraparound).
fn stamp_to_epoch(month: u32, day: u32, hour: u32, min: u32, sec: u32, ms: u32) -> anyhow::Result<u64> {
	let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis() as u64;
	let now_y = civil_from_unix((now / 1000) as i64).0 as i64;

	let mut best: Option<u64> = None;
	for y in [now_y, now_y - 1, now_y + 1] {
		let Some(epoch) = unix_from_civil(y as i32, month, day, hour, min, sec) else {
			continue;
		};
		let t = (epoch as u64) * 1000 + ms as u64;
		let diff = t.abs_diff(now);
		if best.is_none_or(|b| diff < b.abs_diff(now)) {
			best = Some(t);
		}
	}
	best.ok_or_else(|| anyhow::anyhow!("no valid date for {month}-{day}"))
}

// --- civil calendar conversions (Howard Hinnant's algorithms) ---

fn days_from_civil(y: i32, m: u32, d: u32) -> i64 {
	let y = y as i64 - (m <= 2) as i64;
	let era = if y >= 0 { y } else { y - 399 } / 400;
	let yoe = y - era * 400;
	let doy = (153 * (m as i64 + if m > 2 { -3 } else { 9 }) + 2) / 5 + d as i64 - 1;
	let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
	era * 146097 + doe - 719468
}

fn unix_from_civil(y: i32, m: u32, d: u32, h: u32, mi: u32, s: u32) -> Option<i64> {
	if !(1..=12).contains(&m) || !(1..=31).contains(&d) || h > 23 || mi > 59 || s > 60 {
		return None;
	}
	Some(days_from_civil(y, m, d) * 86400 + (h as i64) * 3600 + (mi as i64) * 60 + s as i64)
}

fn civil_from_unix(t: i64) -> (i32, u32, u32, u32, u32, u32) {
	let days = t.div_euclid(86400);
	let secs = t.rem_euclid(86400);
	let (h, mi, s) = (secs / 3600, (secs % 3600) / 60, secs % 60);

	let z = days + 719468;
	let era = if z >= 0 { z } else { z - 146096 } / 146097;
	let doe = z - era * 146097;
	let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
	let y = yoe + era * 400;
	let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
	let mp = (5 * doy + 2) / 153;
	let d = doy - (153 * mp + 2) / 5 + 1;
	let m = if mp < 10 { mp + 3 } else { mp - 9 };
	let y = if m <= 2 { y + 1 } else { y };
	(y as i32, m as u32, d as u32, h as u32, mi as u32, s as u32)
}

#[cfg(test)]
mod tests {
	use super::*;

	#[test]
	fn parse_basic() {
		let line = "03-10 14:22:33.456  1234  5678 I StatusBarHider: hello world";
		let r = parse_threadtime(line).unwrap().unwrap();
		assert_eq!(r.pid, 1234);
		assert_eq!(r.tid, 5678);
		assert_eq!(r.level, Level::Info);
		assert_eq!(r.tag, "StatusBarHider");
		assert_eq!(r.msg, "hello world");
		assert_eq!(r.time, "14:22:33.456");
	}

	#[test]
	fn parse_tag_with_spaces_and_colon_in_msg() {
		let line = "01-02 00:00:01.000    10    20 W Some Tag  : message: with colon";
		let r = parse_threadtime(line).unwrap().unwrap();
		assert_eq!(r.tag, "Some Tag");
		assert_eq!(r.msg, "message: with colon");
	}

	#[test]
	fn continuation_returns_none() {
		assert!(parse_threadtime("    at com.foo.Bar.baz(Bar.java:42)").unwrap().is_none());
		assert!(parse_threadtime("Caused by: java.lang.RuntimeException: boom").unwrap().is_none());
		assert!(parse_threadtime("--------- beginning of main").unwrap().is_none());
	}

	#[test]
	fn epoch_is_close_to_now() {
		let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
		let line = format!(
			"{} 12:00:00.000 1 1 I T : x",
			now_time_md()
		);
		let r = parse_threadtime(&line).unwrap().unwrap();
		assert!(r.epoch_ms.abs_diff(now) < 2 * 86_400_000, "epoch drift too big");
	}

	fn now_time_md() -> String {
		let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as i64;
		let (_, mo, d, _, _, _) = civil_from_unix(now / 1000);
		format!("{mo:02}-{d:02}")
	}

	#[test]
	fn roundtrip_civil() {
		for t in [0i64, 1700000000, 1735689600, 1743465600] {
			let (y, m, d, h, mi, s) = civil_from_unix(t);
			assert_eq!(unix_from_civil(y, m, d, h, mi, s).unwrap(), t);
		}
	}
}
