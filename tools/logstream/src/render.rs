use std::io::Write;

use crate::buffer::{Banner, Entry};
use crate::parse::{Level, Record};

const RESET: &str = "\x1b[0m";
const DIM: &str = "\x1b[2m";
const BOLD: &str = "\x1b[1m";

fn level_color(l: Level) -> &'static str {
	match l {
		Level::Verbose => "\x1b[90m",  // grey
		Level::Debug => "\x1b[36m",    // cyan
		Level::Info => "\x1b[32m",     // green
		Level::Warn => "\x1b[33m",     // yellow
		Level::Error => "\x1b[31m",    // red
		Level::Fatal => "\x1b[1;31m",  // bold red
	}
}

fn banner_color(b: &Banner) -> &'static str {
	match b {
		Banner::Crash | Banner::Died { .. } => "\x1b[1;31m",
		Banner::Restart { .. } | Banner::Started { .. } => "\x1b[1;33m",
		Banner::NotRunning => "\x1b[34m",
	}
}

/// Format the delta between two entries: `+12ms`, `+1.4s`, `+2m10s`.
fn fmt_delta(prev_ms: u64, cur_ms: u64) -> String {
	let d = cur_ms.saturating_sub(prev_ms);
	if d < 1000 {
		format!("+{d}ms")
	} else if d < 60_000 {
		format!("+{:.1}s", d as f64 / 1000.0)
	} else {
		let m = d / 60_000;
		let s = (d % 60_000) / 1000;
		format!("+{m}m{s:02}s")
	}
}

pub struct PlainRenderer {
	pub color: bool,
	/// Tag column width (auto-grown, min 16).
	tag_width: usize,
	/// Epoch of previously printed entry (for delta).
	prev: Option<(u64, u64)>, // (epoch, last_repeat_ms)
	/// Collapsible pending group (TTY only): rewritten in place on repeats.
	pending: Option<Entry>,
	/// Terminal rows the pending entry occupies (for cursor-up rewrite).
	pending_rows: usize,
	/// Collapse window.
	quiet_ms: u64,
	/// Delta baseline of the pending group (`None` when the group's first line
	/// had no predecessor), so collapsing doesn't rewrite the delta already shown.
	pending_base: Option<u64>,
}

impl PlainRenderer {
	pub fn new(color: bool) -> Self {
		Self {
			color,
			tag_width: 16,
			prev: None,
			pending: None,
			pending_rows: 0,
			quiet_ms: 2000,
			pending_base: None,
		}
	}

	fn paint(&self, s: &str, code: &str) -> String {
		if self.color {
			format!("{code}{s}{RESET}")
		} else {
			s.to_string()
		}
	}

	fn dim(&self, s: &str) -> String {
		if self.color {
			format!("{DIM}{s}{RESET}")
		} else {
			s.to_string()
		}
	}

	/// Render one entry to ANSI (or plain) text lines.
	pub fn render(&mut self, e: &Entry) -> String {
		if let Some(b) = &e.banner {
			let text = b.text();
			let bar = "─".repeat(3);
			let line = format!("{bar} {text} {bar}");
			self.prev = Some((e.last_ms, e.last_ms));
			return self.paint(&line, banner_color(b));
		}

		let r = &e.record;
		self.tag_width = self.tag_width.max(r.tag.len()).min(24);

		// delta since previous line (based on the *last* repeat so stacked
		// repeats show the gap from the previous distinct line)
		let delta = match self.prev {
			Some((_, last)) => fmt_delta(last, r.epoch_ms),
			None => " ".to_string(),
		};
		self.prev = Some((r.epoch_ms, e.last_ms));

		let time = if r.time.is_empty() { "—".to_string() } else { r.time.clone() };
		let lvl = r.level.as_char().to_string();
		let tag = format!("{:<w$}", r.tag, w = self.tag_width);
		let count = if e.count > 1 {
			self.paint(&format!(" ×{}", e.count), BOLD)
		} else {
			String::new()
		};

		// message (first line; stacktrace lines indented under the tag column)
		let indent = " ".repeat(self.tag_width);
		let mut msg_out = String::new();
		for (i, ml) in r.msg.split('\n').enumerate() {
			if i == 0 {
				msg_out.push_str(ml);
			} else {
				msg_out.push('\n');
				msg_out.push_str(&indent);
				msg_out.push_str(ml);
			}
		}

		format!(
			"{} {} {} {}{}{}",
			self.dim(&format!("{time:>12}")),
			self.dim(&format!("{delta:>8}")),
			self.paint(&lvl, level_color(r.level)),
			self.paint(&tag, level_color(r.level)),
			": ",
			msg_out
		) + &count
	}

	/// Print entry to a writer, collapsing consecutive repeats into `×N`
	/// when stdout is an interactive TTY (rewrites the line in place).
	/// Non-TTY output gets every line uncollapsed (machine-friendly).
	pub fn write_to(&mut self, w: &mut impl Write, e: &Entry) -> std::io::Result<()> {
		let interactive = self.color; // color == tty in main
		if interactive && self.is_repeat(e) {
			// merge into the pending group, then re-render it in place with the
			// *original* delta (delta computed from `pending_base`).
			if let Some(p) = self.pending.as_mut() {
				p.count += 1;
				p.last_ms = e.last_ms;
			}
			let merged = self.pending.clone().expect("checked by is_repeat");
			// restore the baseline the group was first rendered with
			self.prev = self.pending_base.map(|b| (b, b));
			let s = self.render(&merged);
			self.pending = Some(merged);
			self.pending_rows = s.split('\n').count();
			// next distinct line's delta must measure from the last repeat
			if let Some(p) = self.pending.as_ref() {
				self.prev = Some((p.record.epoch_ms, p.last_ms));
			}
			write!(w, "\x1b[{}A\r\x1b[J", self.pending_rows.saturating_sub(1))?;
			writeln!(w, "{s}")?;
			return w.flush();
		}
		// Remember the delta baseline *before* rendering so repeats keep it.
		self.pending_base = self.prev.map(|(_, last)| last);
		let s = self.render(e);
		self.pending_rows = s.split('\n').count();
		self.pending = Some(e.clone());
		writeln!(w, "{s}")?;
		w.flush()
	}

	fn is_repeat(&self, e: &Entry) -> bool {
		let Some(p) = &self.pending else { return false };
		p.banner.is_none()
			&& e.banner.is_none()
			&& p.record.tag == e.record.tag
			&& p.record.level == e.record.level
			&& p.record.msg == e.record.msg
			&& e.record.epoch_ms.saturating_sub(p.last_ms) <= self.quiet_ms
	}
}

/// JSONL record for `--json`.
pub fn json_line(r: &Record) -> String {
	serde_json::json!({
		"ts": r.iso_time(),
		"ts_ms": r.epoch_ms,
		"time": r.time,
		"pid": r.pid,
		"tid": r.tid,
		"level": r.level.name(),
		"tag": r.tag,
		"msg": r.msg,
	})
	.to_string()
}

/// Raw line for `--out` (no ANSI): `HH:MM:SS.mmm  PID  TID L tag: msg`.
/// Stacktrace continuations are indented under the message.
pub fn raw_line(r: &Record) -> String {
	format!(
		"{} {:>6} {:>6} {} {}: {}",
		r.time,
		r.pid,
		r.tid,
		r.level.as_char(),
		r.tag,
		r.msg.replace('\n', "\n    ")
	)
}

#[cfg(test)]
mod tests {
	use super::*;

	#[test]
	fn delta_formats() {
		assert_eq!(fmt_delta(0, 500), "+500ms");
		assert_eq!(fmt_delta(0, 1400), "+1.4s");
		assert_eq!(fmt_delta(0, 130_000), "+2m10s");
	}

	#[test]
	fn plain_render_no_color_uses_columns() {
		let mut p = PlainRenderer::new(false);
		let e = Entry {
			record: Record {
				epoch_ms: 1000,
				time: "12:00:00.000".into(),
				pid: 1,
				tid: 2,
				level: Level::Error,
				tag: "MyTag".into(),
				msg: "boom".into(),
			},
			count: 2,
			banner: None,
			last_ms: 1000,
		};
		let out = p.render(&e);
		assert!(out.contains("12:00:00.000"));
		assert!(out.contains("E"));
		assert!(out.contains("MyTag"));
		assert!(out.contains("boom"));
		assert!(out.contains("×2"));
		assert!(!out.contains('\x1b'));
	}
}
