use std::collections::VecDeque;
use std::time::Duration;

use crate::parse::{Level, Record};

/// One displayable item in the ring buffer.
#[derive(Debug, Clone)]
pub struct Entry {
	pub record: Record,
	/// Number of collapsed repeats (1 = standalone).
	pub count: u32,
	/// Kind: normal line, or an in-band banner (restart/crash) synthesized by us.
	pub banner: Option<Banner>,
	/// Epoch ms of the most recent repeat (for delta display).
	pub last_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Banner {
	Restart { old_pid: u32, new_pid: u32 },
	Died { pid: u32 },
	Crash,
	Started { pid: u32 },
	NotRunning,
}

impl Banner {
	pub fn text(&self) -> String {
		match self {
			Banner::Restart { old_pid, new_pid } => format!("↻ process restarted  {old_pid} → {new_pid}"),
			Banner::Died { pid } => format!("✗ process died  (pid {pid})"),
			Banner::Crash => "✗ CRASH  uncaught exception (see FATAL below)".to_string(),
			Banner::Started { pid } => format!("✓ process started  (pid {pid})"),
			Banner::NotRunning => "… waiting for app process to start".to_string(),
		}
	}
}

/// Which pids to keep.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PidMode {
	/// Only the app's pid(s).
	App,
	/// Everything (no pid filtering).
	All,
}

#[derive(Debug, Clone)]
pub struct Filter {
	pub pid_mode: PidMode,
	pub app_pids: Vec<u32>,
	pub min_level: Level,
	/// Tag prefixes to keep (empty = all).
	pub allow: Vec<String>,
	/// Tag prefixes to drop.
	pub deny: Vec<String>,
	/// Live search query (empty = no search). Compiled form in `search_re`.
	pub search: String,
	/// Compiled `search` (`(?i)`-wrapped); `None` when empty or invalid.
	pub search_re: Option<regex::Regex>,
	/// Package name — lines from *other* processes that mention it are kept
	/// (ActivityManager start/die messages, crash reporters).
	pub package: String,
}

impl Filter {
	pub fn new(pid_mode: PidMode, package: impl Into<String>) -> Self {
		Self {
			pid_mode,
			app_pids: vec![],
			min_level: Level::Verbose,
			allow: vec![],
			deny: vec![],
			search: String::new(),
			search_re: None,
			package: package.into(),
		}
	}

	/// Set the search query; compiles it as a case-insensitive regex.
	/// Returns `false` when the pattern is invalid (query still stored so the
	/// user can see/fix it — it then matches nothing).
	pub fn set_search(&mut self, q: &str) -> bool {
		self.search = q.to_string();
		self.search_re = if q.is_empty() {
			None
		} else {
			regex::Regex::new(&format!("(?i)(?:{q})")).ok()
		};
		q.is_empty() || self.search_re.is_some()
	}
	/// System tags worth keeping when the line comes from *another* process
	/// but mentions our package (crash reports, proc start/die, kills).
	/// Anything else from a foreign pid is noise (`adbd` echoing our own
	/// `pidof` call, `AppsFilter`, `Icon`, …).
	const SYSTEM_KEEP: [&'static str; 8] = [
		"ActivityManager",
		"ActivityTaskManager",
		"AndroidRuntime",
		"WindowManager",
		"lowmemorykiller",
		"libprocessgroup",
		"libc",
		"System.err",
	];

	/// Only the app's pid, or a *relevant* system line about the app.
	pub fn matches_pid(&self, rec: &Record) -> bool {
		match self.pid_mode {
			PidMode::All => true,
			PidMode::App => {
				let ours = !self.app_pids.is_empty() && self.app_pids.contains(&rec.pid);
				if ours {
					return true;
				}
				// foreign pid (or app not running): keep only system lines that
				// talk about our package
				!self.package.is_empty()
					&& rec.msg.contains(&self.package)
					&& Self::SYSTEM_KEEP.contains(&rec.tag.as_str())
			}
		}
	}

	pub fn matches_tags(&self, rec: &Record) -> bool {
		if self.deny.iter().any(|t| rec.tag.starts_with(t.as_str())) {
			return false;
		}
		if self.allow.is_empty() {
			return true;
		}
		self.allow.iter().any(|t| rec.tag.starts_with(t.as_str()))
	}

	pub fn matches_level(&self, rec: &Record) -> bool {
		rec.level >= self.min_level
	}

	pub fn matches_search(&self, rec: &Record) -> bool {
		if self.search.is_empty() {
			return true;
		}
		// invalid regex → matches nothing (status bar shows the error)
		self.search_re.as_ref().is_some_and(|re| re.is_match(&rec.msg) || re.is_match(&rec.tag))
	}

	/// Everything the *display* cares about: pid + tags + level + search.
	/// `--out`/`--json` mirror exactly what is shown.
	pub fn keep(&self, rec: &Record) -> bool {
		self.matches_pid(rec) && self.matches_tags(rec) && self.matches_level(rec) && self.matches_search(rec)
	}

	/// Scope filters only (pid + tags + level) — decides what is *stored* in
	/// the ring buffer, so widening the search later can still find history.
	pub fn keep_scope(&self, rec: &Record) -> bool {
		self.matches_pid(rec) && self.matches_tags(rec) && self.matches_level(rec)
	}
}

/// Fixed-capacity log buffer with repeat-collapsing.
pub struct Buffer {
	items: VecDeque<Entry>,
	cap: usize,
	/// Window for collapsing consecutive identical lines.
	quiet: Duration,
	/// How many items were popped from the front since last [`Buffer::evicted`] call.
	evicted: usize,
}

impl Buffer {
	pub fn new(cap: usize) -> Self {
		Self {
			items: VecDeque::with_capacity(cap.min(4096)),
			cap: cap.max(16),
			quiet: Duration::from_millis(2000),
			evicted: 0,
		}
	}

	/// Number of items evicted from the front since the last call (and reset).
	pub fn evicted(&mut self) -> usize {
		std::mem::take(&mut self.evicted)
	}

	pub fn len(&self) -> usize {
		self.items.len()
	}

	pub fn clear(&mut self) {
		self.items.clear();
		self.evicted = 0;
	}

	pub fn items(&self) -> &VecDeque<Entry> {
		&self.items
	}

	/// Push a record; collapse into the previous entry when the same
	/// (tag, level, message) repeats within the quiet window.
	pub fn push(&mut self, rec: Record) {
		if let Some(prev) = self.items.back_mut() {
			let same = prev.banner.is_none()
				&& prev.record.tag == rec.tag
				&& prev.record.level == rec.level
				&& prev.record.msg == rec.msg
				&& rec.epoch_ms.saturating_sub(prev.last_ms) <= self.quiet.as_millis() as u64;
			if same {
				prev.count += 1;
				prev.last_ms = rec.epoch_ms;
				return;
			}
		}
		self.items.push_back(Entry {
			last_ms: rec.epoch_ms,
			record: rec,
			count: 1,
			banner: None,
		});
		self.trim();
	}

	/// Push a synthetic banner line (restart/crash/died).
	pub fn push_banner(&mut self, banner: Banner, at_ms: u64) {
		let record = Record {
			epoch_ms: at_ms,
			time: String::new(),
			pid: 0,
			tid: 0,
			level: Level::Warn,
			tag: "logstream".into(),
			msg: banner.text(),
		};
		self.items.push_back(Entry {
			record,
			count: 1,
			banner: Some(banner),
			last_ms: at_ms,
		});
		self.trim();
	}

	fn trim(&mut self) {
		while self.items.len() > self.cap {
			self.items.pop_front();
			self.evicted += 1;
		}
	}
}

#[cfg(test)]
mod tests {
	use super::*;
	use crate::parse::Level;

	fn rec(tag: &str, msg: &str, ms: u64) -> Record {
		Record {
			epoch_ms: ms,
			time: "12:00:00.000".into(),
			pid: 1,
			tid: 1,
			level: Level::Info,
			tag: tag.into(),
			msg: msg.into(),
		}
	}

	#[test]
	fn collapses_repeats_within_window() {
		let mut b = Buffer::new(16);
		b.push(rec("T", "a", 0));
		b.push(rec("T", "a", 100));
		b.push(rec("T", "a", 200));
		assert_eq!(b.len(), 1);
		assert_eq!(b.items().back().unwrap().count, 3);
	}

	#[test]
	fn separates_after_quiet_window() {
		let mut b = Buffer::new(16);
		b.push(rec("T", "a", 0));
		b.push(rec("T", "a", 5000));
		assert_eq!(b.len(), 2);
	}

	#[test]
	fn separates_different_msg() {
		let mut b = Buffer::new(16);
		b.push(rec("T", "a", 0));
		b.push(rec("T", "b", 100));
		assert_eq!(b.len(), 2);
	}

	#[test]
	fn respects_capacity() {
		let mut b = Buffer::new(16);
		for i in 0..100 {
			b.push(rec("T", &format!("m{i}"), i * 3000));
		}
		assert_eq!(b.len(), 16);
	}

	#[test]
	fn filter_pid_app() {
		let mut f = Filter::new(PidMode::App, "com.example.app");
		f.app_pids = vec![42];
		assert!(f.keep(&rec("T", "hello", 0).with_pid(42)));
		assert!(!f.keep(&rec("T", "hello", 0).with_pid(7)));
		// external line mentioning the package is kept
		assert!(f.keep(&rec("ActivityManager", "Start com.example.app", 0).with_pid(1)));
		// foreign pid + relevant tag, but no package mention → dropped
		assert!(!f.keep(&rec("ActivityManager", "Start other.app", 0).with_pid(1)));
		f.app_pids.clear();
		// not running: only system lines mentioning the package
		assert!(f.keep(&rec("ActivityManager", "kill com.example.app now", 0).with_pid(1)));
		assert!(!f.keep(&rec("T", "unrelated", 0).with_pid(1)));
		// foreign pid, package mentioned, but irrelevant tag → noise, dropped
		assert!(!f.keep(&rec("adbd", "pidof -s com.example.app", 0).with_pid(1)));
		assert!(!f.keep(&rec("AppsFilter", "interaction com.example.app", 0).with_pid(1)));
	}

	#[test]
	fn filter_deny_wins_over_allow() {
		let mut f = Filter::new(PidMode::All, "");
		f.allow = vec!["Foo".into()];
		f.deny = vec!["FooBar".into()];
		assert!(f.keep(&rec("Foo", "x", 0)));
		assert!(!f.keep(&rec("FooBar", "x", 0)));
	}

	#[test]
	fn filter_min_level() {
		let mut f = Filter::new(PidMode::All, "");
		f.min_level = Level::Warn;
		assert!(!f.keep(&rec("T", "i", 0)));
		let mut w = rec("T", "w", 0);
		w.level = Level::Warn;
		assert!(f.keep(&w));
	}

	#[test]
	fn search_is_case_insensitive_regex() {
		let mut f = Filter::new(PidMode::All, "");
		assert!(f.set_search("boom|bang"));
		assert!(f.matches_search(&rec("T", "it went BOOM", 0)));
		assert!(f.matches_search(&rec("T", "bang!", 0)));
		assert!(!f.matches_search(&rec("T", "quiet", 0)));
		f.set_search("");
		assert!(f.matches_search(&rec("T", "anything", 0)));
	}

	#[test]
	fn invalid_regex_matches_nothing() {
		let mut f = Filter::new(PidMode::All, "");
		assert!(!f.set_search("("));
		assert!(!f.matches_search(&rec("T", "anything", 0)));
	}

	impl Record {
		fn with_pid(mut self, pid: u32) -> Self {
			self.pid = pid;
			self
		}
	}
}
