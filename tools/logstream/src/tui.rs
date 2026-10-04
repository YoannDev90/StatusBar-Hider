use std::time::Duration;

use crossterm::event::{self, Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers, MouseEvent, MouseEventKind};
use ratatui::layout::{Constraint, Layout};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::Paragraph;
use ratatui::Frame;

use crate::buffer::{Banner, Buffer, Entry, Filter, PidMode};
use crate::parse::{Level, Record};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum InputMode {
	Normal,
	/// `/` — live search.
	Search,
	/// `+` / `-` — tag allow/deny.
	Tag { allow: bool },
	/// `:` — level filter (single char, applied immediately then back to Normal).
	Level,
}

pub struct App {
	pub buffer: Buffer,
	pub filter: Filter,
	/// Frozen (paused) snapshot of `lines`; `None` = following the live tail.
	/// This is what makes scrolling work under a running stream: the snapshot
	/// is independent of new arrivals, so the view stops jumping.
	frozen: Option<Vec<Line<'static>>>,
	/// First visible line inside the frozen snapshot.
	frozen_top: usize,
	/// Body height from the last frame (used as page size).
	last_height: usize,
	/// Draft input being typed in Search/Tag mode.
	pub input: String,
	/// Offset of the cursor within input (always at end here).
	input_mode: InputMode,
	/// Indices into buffer of items matching the current filter (recomputed).
	view: Vec<usize>,
	/// Display-line count per `view` entry (stacktraces span several lines).
	/// Parallel to `view`; lets us splice incrementally instead of rebuilding.
	view_lines: Vec<u32>,
	/// Item indices in `view` matching the search (for n/N navigation).
	match_positions: Vec<usize>,
	current_match: usize,
	/// Live (unfrozen) display lines, appended/spliced incrementally.
	lines: Vec<Line<'static>>,
	/// Status line message (transient — shown for `STATUS_SECS` then reverted
	/// to the keybind hints).
	pub status: String,
	/// Last seen `status` value, to timestamp changes without touching every
	/// assignment site.
	last_status: String,
	/// When `status` last changed.
	status_at: Option<std::time::Instant>,
	/// Set when the app process is not running.
	pub not_running: bool,
	/// Total records seen (including filtered out).
	pub seen: u64,
	/// Total records kept in view.
	pub kept: u64,
}

impl App {
	pub fn new(buffer: Buffer, filter: Filter) -> Self {
		let mut app = Self {
			buffer,
			filter,
			frozen: None,
			frozen_top: 0,
			last_height: 24,
			input: String::new(),
			input_mode: InputMode::Normal,
			view: Vec::new(),
			view_lines: Vec::new(),
			match_positions: Vec::new(),
			current_match: 0,
			lines: Vec::new(),
			status: String::new(),
			last_status: String::new(),
			status_at: None,
			not_running: false,
			seen: 0,
			kept: 0,
		};
		app.refilter();
		app
	}

	/// Full rebuild of `view` / `view_lines` / `lines` — only for filter
	/// changes and clears. Streaming uses the incremental paths below.
	pub fn refilter(&mut self) {
		self.view.clear();
		self.view_lines.clear();
		self.lines.clear();
		for i in 0..self.buffer.len() {
			let pos = self.view.len();
			let built = {
				let e = &self.buffer.items()[i];
				if !(e.banner.is_some() || self.filter.keep(&e.record)) {
					continue;
				}
				self.entry_lines(e, pos)
			};
			self.view.push(i);
			self.view_lines.push(built.len() as u32);
			self.lines.extend(built);
		}
		self.kept = self.view.len() as u64;
		self.recompute_matches();
		// a filter change invalidates any frozen snapshot (it shows old styling)
		self.unfreeze();
	}

	fn recompute_matches(&mut self) {
		self.match_positions.clear();
		if self.filter.search.is_empty() {
			self.current_match = 0;
			return;
		}
		for (pos, &i) in self.view.iter().enumerate() {
			if self.filter.matches_search(&self.buffer.items()[i].record) {
				self.match_positions.push(pos);
			}
		}
		if self.current_match >= self.match_positions.len() {
			self.current_match = self.match_positions.len().saturating_sub(1);
		}
	}

	pub fn push_record(&mut self, rec: Record) {
		self.seen += 1;
		// Store only what passes the scope filters (pid/tags/level). The ring
		// buffer would otherwise fill with system spam in seconds and evict
		// the app's own history.
		if !self.filter.keep_scope(&rec) {
			return;
		}
		self.buffer.push(rec);
		self.refresh_tail();
	}

	pub fn push_banner(&mut self, banner: Banner, at_ms: u64) {
		self.buffer.push_banner(banner, at_ms);
		self.refresh_tail();
	}

	/// Incremental tail update. Keeps `view`/`view_lines`/`lines` consistent
	/// with ring-buffer eviction: when N items leave the front, every
	/// remaining buffer index shifts down by N — they must all be rebased,
	/// not just the first N dropped.
	fn refresh_tail(&mut self) {
		let len = self.buffer.len();
		if len == 0 {
			self.view.clear();
			self.view_lines.clear();
			self.lines.clear();
			self.recompute_matches();
			return;
		}
		let evicted = self.buffer.evicted();
		if evicted > 0 {
			// view is sorted ascending → the first k indices are the ones < evicted
			let drop_n = self.view.iter().take_while(|&&i| i < evicted).count();
			if drop_n > 0 {
				let removed: usize = self.view_lines.drain(..drop_n).map(|c| c as usize).sum();
				self.view.drain(..drop_n);
				self.lines.drain(..removed.min(self.lines.len()));
			}
			// rebase every remaining index
			for i in self.view.iter_mut() {
				*i -= evicted;
			}
			// a frozen snapshot is an independent copy: clamp it, don't rebase
			if let Some(f) = self.frozen.as_mut() {
				// snapshot indices are unaffected (it's a copy of lines)
				let _ = f;
			}
		}
		// newest entry (banner always kept; records were pre-filtered at push)
		let newest = len - 1;
		let keep = {
			let e = &self.buffer.items()[newest];
			e.banner.is_some() || self.filter.keep(&e.record)
		};
		let last_in_view = self.view.last().copied();
		if keep && last_in_view != Some(newest) {
			let pos = self.view.len();
			let built = self.entry_lines(&self.buffer.items()[newest].clone(), pos);
			self.view.push(newest);
			self.view_lines.push(built.len() as u32);
			self.lines.extend(built);
		} else if keep && last_in_view == Some(newest) {
			// collapsed repeat (×N) or duplicate push: refresh that entry's lines
			let pos = self.view.len() - 1;
			let start: usize = self.view_lines[..pos].iter().map(|&c| c as usize).sum();
			let old = self.view_lines[pos] as usize;
			let built = self.entry_lines(&self.buffer.items()[newest].clone(), pos);
			let new_len = built.len();
			let end = (start + old).min(self.lines.len());
			if start <= end {
				self.lines.splice(start..end, built);
			}
			self.view_lines[pos] = new_len as u32;
		}
		self.kept = self.view.len() as u64;
		self.recompute_matches();
	}

	/// Split into layout areas: header, body, footer.
	pub fn render(&mut self, frame: &mut Frame) {
		let areas = Layout::vertical([Constraint::Length(1), Constraint::Min(1), Constraint::Length(1)]).split(frame.area());
		self.last_height = areas[1].height as usize;
		frame.render_widget(self.header(), areas[0]);
		frame.render_widget(self.body(areas[1]), areas[1]);
		frame.render_widget(self.footer(), areas[2]);
	}

	fn header(&self) -> Paragraph<'_> {
		let pid = self
			.filter
			.app_pids
			.first()
			.map(|p| p.to_string())
			.unwrap_or_else(|| if self.filter.pid_mode == PidMode::All { "all".into() } else { "—".into() });
		let level = format!("≥{}", self.filter.min_level.as_char());
		let search = if self.filter.search.is_empty() {
			String::new()
		} else {
			format!("  /{}/ {} match{}", self.filter.search, self.match_positions.len(), if self.match_positions.len() == 1 { "" } else { "es" })
		};
		let tagf = if self.filter.allow.is_empty() && self.filter.deny.is_empty() {
			String::new()
		} else {
			let a: Vec<String> = self.filter.allow.iter().map(|t| format!("+{t}")).collect();
			let d: Vec<String> = self.filter.deny.iter().map(|t| format!("-{t}")).collect();
			format!("  {}", [a, d].concat().join(" "))
		};
		let run = if self.not_running { "● WAIT" } else { "● LIVE" };
		let run_color = if self.not_running { Color::Yellow } else { Color::Green };

		let line = Line::from(vec![
			Span::styled(" logstream ", Style::new().fg(Color::Black).bg(Color::Cyan).bold()),
			Span::raw(" "),
			Span::styled(run, Style::new().fg(run_color).bold()),
			Span::raw(format!(" {pid}")),
			Span::styled(format!(" {level}"), Style::new().fg(Color::DarkGray)),
			Span::styled(tagf, Style::new().fg(Color::Magenta)),
			Span::styled(search, Style::new().fg(Color::Yellow)),
			Span::styled(format!("  {} lines", self.view.len()), Style::new().fg(Color::DarkGray)),
		]);
		Paragraph::new(line).style(Style::new().bg(Color::Gray))
	}

	fn body(&self, area: ratatui::layout::Rect) -> Paragraph<'static> {
		let height = area.height as usize;
		let visible: Vec<Line<'static>> = if let Some(snap) = &self.frozen {
			// paused: scroll inside the frozen snapshot, new arrivals don't move it
			let max_top = snap.len().saturating_sub(height);
			let top = self.frozen_top.min(max_top);
			snap[top..(top + height).min(snap.len())].to_vec()
		} else {
			// following the tail
			let total = self.lines.len();
			let start = total.saturating_sub(height);
			self.lines[start..].to_vec()
		};
		Paragraph::new(visible).style(Style::new().bg(Color::Black))
	}

	/// Display lines for one entry: header + stacktrace continuations.
	fn entry_lines(&self, e: &Entry, pos: usize) -> Vec<Line<'static>> {
		let mut out = Vec::new();
		if let Some(b) = &e.banner {
			let color = match b {
				Banner::Crash | Banner::Died { .. } => Color::Red,
				Banner::Restart { .. } | Banner::Started { .. } => Color::LightYellow,
				Banner::NotRunning => Color::Blue,
			};
			out.push(
				Line::from(vec![
					Span::styled("━━━ ", Style::new().fg(color).dim()),
					Span::styled(e.record.msg.clone(), Style::new().fg(color).bold()),
					Span::styled(" ━━━", Style::new().fg(color).dim()),
				])
				.style(Style::new().bg(Color::Rgb(30, 20, 20))),
			);
			return out;
		}

		let r = &e.record;
		// cursor on the match n/N is parked on (view is already search-filtered)
		let is_current = self.match_positions.get(self.current_match) == Some(&pos);
		let lvl_style = Style::new().fg(match r.level {
			Level::Verbose => Color::DarkGray,
			Level::Debug => Color::Cyan,
			Level::Info => Color::Green,
			Level::Warn => Color::Yellow,
			Level::Error | Level::Fatal => Color::Red,
		});
		let tag_style = lvl_style.add_modifier(Modifier::BOLD);
		let re = self.filter.search_re.clone();

		let head = vec![
			Span::styled(format!("{:>12} ", r.time), Style::new().fg(Color::DarkGray)),
			Span::styled(format!("{} ", r.level.as_char()), lvl_style),
			Span::styled(format!("{:<20}", r.tag), tag_style),
			Span::raw(": "),
		];

		// message: first line with head, continuation lines (stacktrace) indented
		let msg_lines: Vec<&str> = r.msg.split('\n').collect();
		let indent = " ".repeat(12 + 1 + 1 + 20 + 2);
		for (mi, ml) in msg_lines.iter().enumerate() {
			let mut spans: Vec<Span<'static>> = if mi == 0 {
				head.clone()
			} else {
				vec![Span::raw(indent.clone())]
			};
			spans.extend(highlight_spans(ml, re.as_ref()));
			if mi == 0 && e.count > 1 {
				spans.push(Span::styled(format!(" ×{}", e.count), Style::new().fg(Color::Magenta).bold()));
			}
			let mut line = Line::from(spans);
			if is_current {
				line = line.style(Style::new().bg(Color::Rgb(40, 40, 20)));
			}
			out.push(line);
		}
		out
	}

	fn footer(&self) -> Paragraph<'static> {
		Paragraph::new(self.footer_lines())
	}

	/// Footer as plain text (tests + status assertions).
	#[allow(dead_code)] // exercised by unit tests; handy for debugging
	pub fn footer_text(&self) -> String {
		self.footer_lines()
			.iter()
			.map(|l| l.spans.iter().map(|s| s.content.as_ref()).collect::<String>())
			.collect::<Vec<_>>()
			.join("\n")
	}

	fn footer_lines(&self) -> Vec<Line<'static>> {
		match self.input_mode {
			InputMode::Search => vec![Line::from(vec![
				Span::styled(" search: ", Style::new().fg(Color::Black).bg(Color::Yellow).bold()),
				Span::raw(self.input.clone()),
				Span::styled(" ▌", Style::new().fg(Color::Yellow)),
				Span::styled("  Enter apply · Esc cancel", Style::new().fg(Color::DarkGray)),
			])],
			InputMode::Tag { allow } => {
				let (label, color) = if allow { ("+ tag allow", Color::Green) } else { ("- tag deny", Color::Red) };
				vec![Line::from(vec![
					Span::styled(format!(" {label}: "), Style::new().fg(Color::Black).bg(color).bold()),
					Span::raw(self.input.clone()),
					Span::styled(" ▌", Style::new().fg(color)),
					Span::styled("  prefix match · Enter apply · Esc cancel", Style::new().fg(Color::DarkGray)),
				])]
			}
			InputMode::Level => vec![Line::from(vec![
				Span::styled(" min level: ", Style::new().fg(Color::Black).bg(Color::Blue).bold()),
				Span::styled("v d i w e f", Style::new().fg(Color::White)),
				Span::styled("  (press key) · Esc cancel", Style::new().fg(Color::DarkGray)),
			])],
			InputMode::Normal => {
				let left = if !self.status.is_empty() {
					Line::from(Span::styled(format!(" {} ", self.status), Style::new().fg(Color::Black).bg(Color::Green)))
				} else {
					Line::from(vec![
						Span::styled(" /", Style::new().fg(Color::Yellow)),
						Span::raw("search "),
						Span::styled("n/N", Style::new().fg(Color::Yellow)),
						Span::raw(" match "),
						Span::styled("+/-", Style::new().fg(Color::Yellow)),
						Span::raw(" tag "),
						Span::styled(":e", Style::new().fg(Color::Yellow)),
						Span::raw(" level "),
						Span::styled("c", Style::new().fg(Color::Yellow)),
						Span::raw(" clear "),
						Span::styled("PgUp/Dn", Style::new().fg(Color::Yellow)),
						Span::raw(" scroll "),
						Span::styled("End", Style::new().fg(Color::Yellow)),
						Span::raw(" follow "),
						Span::styled("q", Style::new().fg(Color::Yellow)),
						Span::raw(" quit"),
					])
				};
				let follow_info = if self.frozen.is_some() {
					Span::styled("  ⏸ paused · End=live", Style::new().fg(Color::Yellow))
				} else {
					Span::styled("  ↓ following", Style::new().fg(Color::DarkGray))
				};
				vec![left, Line::from(follow_info)]
			}
		}
	}

	/// Handle one terminal event. Returns `true` when the app should quit.
	pub fn handle_event(&mut self, ev: &Event) -> bool {
		match ev {
			Event::Key(key) if key.kind == KeyEventKind::Press => return self.handle_key(key),
			Event::Mouse(mouse) => self.handle_mouse(mouse),
			_ => {}
		}
		false
	}

	fn handle_mouse(&mut self, m: &MouseEvent) {
		match m.kind {
			MouseEventKind::ScrollUp => self.scroll_view(3),
			MouseEventKind::ScrollDown => self.scroll_view(-3),
			_ => {}
		}
	}

	/// Pause the follow: snapshot the current live lines so new arrivals can't
	/// move what the user is reading. `n > 0` scrolls up (older), `n < 0` down.
	fn scroll_view(&mut self, n: i32) {
		if self.frozen.is_none() {
			// start from the bottom of the live tail
			let snap = self.lines.clone();
			let top = snap.len().saturating_sub(self.last_height);
			self.frozen = Some(snap);
			self.frozen_top = top;
		}
		let Some(snap) = &self.frozen else { return };
		let max_top = snap.len().saturating_sub(self.last_height);
		if n >= 0 {
			self.frozen_top = self.frozen_top.saturating_sub(n as usize);
		} else {
			let next = self.frozen_top.saturating_add((-n) as usize);
			if next >= max_top {
				// scrolled back to the bottom → resume following
				self.unfreeze();
				return;
			}
			self.frozen_top = next;
		}
	}

	/// Jump to the oldest line (still paused).
	fn home(&mut self) {
		if self.frozen.is_none() {
			self.frozen = Some(self.lines.clone());
		}
		self.frozen_top = 0;
	}

	/// Resume following the live tail.
	fn unfreeze(&mut self) {
		self.frozen = None;
		self.frozen_top = 0;
	}

	fn handle_key(&mut self, key: &KeyEvent) -> bool {
		// Ctrl+C always quits.
		if key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL) {
			return true;
		}

		match self.input_mode {
			InputMode::Search | InputMode::Tag { .. } => {
				match key.code {
				KeyCode::Esc => {
					self.input.clear();
					if self.input_mode == InputMode::Search {
						self.filter.set_search("");
						self.refilter();
					}
					self.input_mode = InputMode::Normal;
				}
					KeyCode::Enter => {
						let input = std::mem::take(&mut self.input);
						match self.input_mode {
						InputMode::Search => {
							let ok = self.filter.set_search(&input);
							self.current_match = 0;
							self.status = if input.is_empty() {
								"search cleared".into()
							} else if !ok {
								format!("invalid regex: {input}")
							} else {
								format!("{} matches", self.match_positions.len())
							};
						}
							InputMode::Tag { allow } => {
								if input.is_empty() {
									if allow {
										self.filter.allow.clear();
									} else {
										self.filter.deny.clear();
									}
									self.status = if allow { "allow-list cleared".into() } else { "deny-list cleared".into() };
								} else if allow {
									if !self.filter.allow.contains(&input) {
										self.filter.allow.push(input.clone());
									}
									self.status = format!("+{input}");
								} else {
									if !self.filter.deny.contains(&input) {
										self.filter.deny.push(input.clone());
									}
									self.status = format!("-{input}");
								}
							}
							_ => unreachable!(),
						}
						self.input_mode = InputMode::Normal;
						self.refilter();
					}
				KeyCode::Backspace => {
					self.input.pop();
					if self.input_mode == InputMode::Search {
						self.filter.set_search(&self.input);
						self.current_match = 0;
						self.refilter();
					}
				}
				KeyCode::Char(c) => {
					self.input.push(c);
					// live search: refilter as you type
					if self.input_mode == InputMode::Search {
						self.filter.set_search(&self.input);
						self.current_match = 0;
						self.refilter();
					}
				}
					_ => {}
				}
			}
			InputMode::Level => {
				match key.code {
					KeyCode::Esc => self.input_mode = InputMode::Normal,
					KeyCode::Char(c) => {
						if let Some(l) = Level::from_str(&c.to_string()) {
							self.filter.min_level = l;
							self.status = format!("≥ {}", l.name());
							self.refilter();
						} else {
							self.status = format!("unknown level '{c}'");
						}
						self.input_mode = InputMode::Normal;
					}
					_ => {}
				}
			}
			InputMode::Normal => match key.code {
				KeyCode::Char('q') => return true,
				KeyCode::Char('/') => {
					self.input.clear();
					self.input_mode = InputMode::Search;
				}
				KeyCode::Char('+') => {
					self.input.clear();
					self.input_mode = InputMode::Tag { allow: true };
				}
				KeyCode::Char('-') => {
					self.input.clear();
					self.input_mode = InputMode::Tag { allow: false };
				}
				KeyCode::Char(':') => {
					self.input_mode = InputMode::Level;
				}
				KeyCode::Char('c') => {
					self.buffer.clear();
					self.seen = 0;
					self.unfreeze();
					self.status = "cleared".into();
					self.refilter();
				}
				KeyCode::Char('n') => self.next_match(1),
				KeyCode::Char('N') => self.next_match(-1),
				KeyCode::Char('a') if key.modifiers.contains(KeyModifiers::CONTROL) => {
					self.filter.pid_mode = if self.filter.pid_mode == PidMode::App { PidMode::All } else { PidMode::App };
					self.status = match self.filter.pid_mode {
						PidMode::App => "filter: app pid".into(),
						PidMode::All => "filter: all pids".into(),
					};
					self.refilter();
				}
				KeyCode::PageUp => self.scroll_view(10),
				KeyCode::PageDown => self.scroll_view(-10),
				KeyCode::Home => self.home(),
				KeyCode::End => self.unfreeze(),
				KeyCode::Up => self.scroll_view(1),
				KeyCode::Down => self.scroll_view(-1),
				_ => {}
			},
		}
		false
	}

	fn next_match(&mut self, dir: i32) {
		if self.match_positions.is_empty() {
			self.status = "no matches".into();
			return;
		}
		let len = self.match_positions.len() as i32;
		let cur = self.current_match as i32;
		let next = (cur + dir).rem_euclid(len);
		self.current_match = next as usize;
		let pos = self.match_positions[self.current_match];
		// freeze on a snapshot and park the match near the top of the body
		let line_offset: usize = self.view_lines[..pos].iter().map(|&c| c as usize).sum();
		if self.frozen.is_none() {
			self.frozen = Some(self.lines.clone());
		}
		if let Some(snap) = &self.frozen {
			let max_top = snap.len().saturating_sub(self.last_height);
			self.frozen_top = line_offset.min(max_top);
		}
		self.status = format!("match {}/{}", self.current_match + 1, len);
	}

	/// Expire a transient status message so the keybind hints come back.
	/// Call once per frame.
	pub fn tick(&mut self) {
		const STATUS_SECS: u64 = 4;
		if self.status != self.last_status {
			self.last_status = self.status.clone();
			self.status_at = Some(std::time::Instant::now());
		} else if !self.status.is_empty() {
			if let Some(t) = self.status_at {
				if t.elapsed().as_secs() >= STATUS_SECS {
					self.status.clear();
					self.last_status.clear();
					self.status_at = None;
				}
			}
		}
	}
}

/// Split `text` on matches of the search regex, wrapping them in a highlight
/// style. Returns owned spans (ratatui needs `'static`).
fn highlight_spans(text: &str, re: Option<&regex::Regex>) -> Vec<Span<'static>> {
	let Some(re) = re else {
		return vec![Span::raw(text.to_string())];
	};
	let mut spans = Vec::new();
	let mut last = 0usize;
	for m in re.find_iter(text) {
		let (s, e) = (m.start(), m.end());
		if s == e {
			continue; // empty match: avoid stalling
		}
		if s > last {
			spans.push(Span::raw(text[last..s].to_string()));
		}
		spans.push(Span::styled(
			text[s..e].to_string(),
			Style::new().bg(Color::Yellow).fg(Color::Black).bold(),
		));
		last = e;
	}
	if last < text.len() {
		spans.push(Span::raw(text[last..].to_string()));
	}
	spans
}

/// Poll for terminal events with a timeout; returns quit flag.
pub fn poll_events(app: &mut App, timeout: Duration) -> anyhow::Result<bool> {
	if event::poll(timeout)? {
		let ev = event::read()?;
		return Ok(app.handle_event(&ev));
	}
	Ok(false)
}

#[cfg(test)]
mod tests {
	use super::*;
	use crate::buffer::{Buffer, Filter, PidMode};
	use crate::parse::{Level, Record};
	use ratatui::backend::TestBackend;
	use ratatui::Terminal;

	fn rec(tag: &str, msg: &str, ms: u64) -> Record {
		Record {
			epoch_ms: ms,
			time: "12:00:00.000".into(),
			pid: 42,
			tid: 42,
			level: Level::Info,
			tag: tag.into(),
			msg: msg.into(),
		}
	}

	fn app() -> App {
		let mut filter = Filter::new(PidMode::App, "pkg");
		filter.app_pids = vec![42];
		App::new(Buffer::new(100), filter)
	}

	fn buffer_text(term: &mut Terminal<TestBackend>) -> String {
		let buf = term.backend().buffer();
		let area = *buf.area();
		let mut s = String::new();
		for y in area.top()..area.bottom() {
			for x in area.left()..area.right() {
				s.push_str(buf[(x, y)].symbol());
			}
			s.push('\n');
		}
		s
	}

	#[test]
	fn renders_records_and_header() {
		let mut a = app();
		a.push_record(rec("MyTag", "hello world", 1000));
		let mut term = Terminal::new(TestBackend::new(100, 12)).unwrap();
		term.draw(|f| a.render(f)).unwrap();
		let out = buffer_text(&mut term);
		assert!(out.contains("logstream"), "header missing: {out}");
		assert!(out.contains("MyTag"), "tag missing: {out}");
		assert!(out.contains("hello world"), "msg missing: {out}");
	}

	#[test]
	fn filters_out_other_pids() {
		let mut a = app();
		let mut other = rec("Noise", "should not show", 1000);
		other.pid = 999;
		a.push_record(other);
		a.push_record(rec("Keep", "visible", 1000));
		let mut term = Terminal::new(TestBackend::new(100, 12)).unwrap();
		term.draw(|f| a.render(f)).unwrap();
		let out = buffer_text(&mut term);
		assert!(!out.contains("should not show"), "leaked: {out}");
		assert!(out.contains("visible"), "missing: {out}");
	}

	#[test]
	fn search_filters_and_highlights() {
		let mut a = app();
		a.push_record(rec("T", "alpha one", 1000));
		a.push_record(rec("T", "beta two", 2000));
		a.filter.set_search("beta");
		a.refilter();
		let mut term = Terminal::new(TestBackend::new(100, 12)).unwrap();
		term.draw(|f| a.render(f)).unwrap();
		let out = buffer_text(&mut term);
		assert!(out.contains("beta"), "match missing: {out}");
		assert!(!out.contains("alpha"), "non-match leaked: {out}");
	}

	#[test]
	fn collapse_repeats_into_count() {
		let mut a = app();
		for i in 0..5 {
			a.push_record(rec("T", "spam", 1000 + i * 100));
		}
		assert_eq!(a.buffer.len(), 1);
		assert_eq!(a.buffer.items()[0].count, 5);
		let mut term = Terminal::new(TestBackend::new(100, 12)).unwrap();
		term.draw(|f| a.render(f)).unwrap();
		let out = buffer_text(&mut term);
		assert!(out.contains("×5"), "count missing: {out}");
	}

	#[test]
	fn stacktrace_renders_as_extra_lines() {
		let mut a = app();
		a.push_record(rec("T", "boom\n\tat foo.Bar(Bar.java:1)\nCaused by: x", 1000));
		let mut term = Terminal::new(TestBackend::new(100, 20)).unwrap();
		term.draw(|f| a.render(f)).unwrap();
		let out = buffer_text(&mut term);
		assert!(out.contains("boom"), "first line missing: {out}");
		assert!(out.contains("at foo.Bar"), "stack line missing: {out}");
		assert!(out.contains("Caused by"), "cause missing: {out}");
	}

	#[test]
	fn key_q_quits_and_slash_opens_search() {
		let mut a = app();
		let quit = Event::Key(crossterm::event::KeyEvent::new(KeyCode::Char('q'), KeyModifiers::NONE));
		assert!(a.handle_event(&quit));

		let slash = Event::Key(crossterm::event::KeyEvent::new(KeyCode::Char('/'), KeyModifiers::NONE));
		assert!(!a.handle_event(&slash));
		let ch = Event::Key(crossterm::event::KeyEvent::new(KeyCode::Char('x'), KeyModifiers::NONE));
		assert!(!a.handle_event(&ch));
		assert_eq!(a.input, "x");
		assert_eq!(a.filter.search, "x"); // live filter while typing
	}

	#[test]
	fn status_expires_back_to_keybinds() {
		let mut a = app();
		a.status = "pid 42".into();
		a.tick(); // registers the change
		assert!(a.footer_text().contains("pid 42"));

		// simulate >4s elapsed: forge status_at into the past
		a.status_at = Some(std::time::Instant::now() - std::time::Duration::from_secs(5));
		a.tick();
		assert!(a.status.is_empty(), "status should have expired");
		assert!(a.footer_text().contains("search"), "keybind hints should return: {}", a.footer_text());
	}

	#[test]
	fn status_refreshed_on_change_is_not_expired() {
		let mut a = app();
		a.status = "match 1/3".into();
		a.tick();
		a.status_at = Some(std::time::Instant::now() - std::time::Duration::from_secs(5));
		// a new status value arrives: timer must reset
		a.status = "match 2/3".into();
		a.tick();
		assert_eq!(a.status, "match 2/3");
	}

	#[test]
	fn scroll_pauses_follow_and_end_resumes() {
		let mut a = app();
		for i in 0..50 {
			a.push_record(rec("T", &format!("line {i}"), 1000 + i * 3000));
		}
		assert!(a.frozen.is_none(), "starts following the tail");

		let pgup = Event::Key(crossterm::event::KeyEvent::new(KeyCode::PageUp, KeyModifiers::NONE));
		a.handle_event(&pgup);
		assert!(a.frozen.is_some(), "PageUp must pause the follow");
		assert!(a.frozen_top < a.lines.len().saturating_sub(a.last_height), "must scroll away from the bottom");
		assert!(a.footer_text().contains("paused"), "footer should show paused: {}", a.footer_text());

		// new arrivals must NOT move the paused view
		let top_before = a.frozen_top;
		let snap_len = a.frozen.as_ref().map(|s| s.len()).unwrap_or(0);
		for i in 50..80 {
			a.push_record(rec("T", &format!("new {i}"), 100000 + i * 3000));
		}
		assert_eq!(a.frozen.as_ref().map(|s| s.len()), Some(snap_len), "snapshot must be stable");
		assert_eq!(a.frozen_top, top_before, "paused position must not drift");

		let end = Event::Key(crossterm::event::KeyEvent::new(KeyCode::End, KeyModifiers::NONE));
		a.handle_event(&end);
		assert!(a.frozen.is_none(), "End resumes follow");
		assert!(a.footer_text().contains("following"), "footer should show following: {}", a.footer_text());
	}

	#[test]
	fn storage_is_scoped_so_spam_cant_evict_app_history() {
		let mut a = app();
		// foreign pid, unrelated → must not enter the buffer at all
		let mut foreign = rec("Noise", "spam from another app", 1000);
		foreign.pid = 999;
		a.push_record(foreign);
		assert_eq!(a.buffer.len(), 0, "out-of-scope record must not be stored");
		assert_eq!(a.seen, 1, "but it is still counted as seen");

		// app records are stored
		for i in 0..10 {
			a.push_record(rec("T", &format!("app line {i}"), 2000 + i * 3000));
		}
		assert_eq!(a.buffer.len(), 10);
		assert_eq!(a.view.len(), 10);
	}

	#[test]
	fn eviction_rebases_view_indices() {
		let mut a = App::new(Buffer::new(16), Filter::new(PidMode::App, "pkg"));
		a.filter.app_pids = vec![42];
		// mix kept and dropped records so view indices are sparse in the buffer
		for i in 0..60u64 {
			let mut r = rec("T", &format!("kept {i}"), 1000 + i * 3000);
			r.pid = 42;
			a.push_record(r);
			let mut noise = rec("Noise", "dropped", 1500 + i * 3000);
			noise.pid = 7;
			a.push_record(noise);
		}
		// buffer is capped at 16; view must point at kept records only
		assert_eq!(a.buffer.len(), 16);
		assert!(a.view.len() <= 16);
		for (n, &idx) in a.view.iter().enumerate() {
			let e = &a.buffer.items()[idx];
			assert_eq!(e.record.tag, "T", "view[{n}] → buffer[{idx}] must be a kept record");
			assert!(e.record.msg.starts_with("kept"), "wrong record: {}", e.record.msg);
		}
		// view indices must be ascending and unique (no corruption)
		assert!(a.view.windows(2).all(|w| w[0] < w[1]), "view indices must be strictly ascending: {:?}", a.view);
	}
}
