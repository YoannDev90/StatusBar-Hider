# App features

What the app ships today. See [How it works](how-it-works.md) for the command
flow behind the hide/restore actions.

## Navigation

Two screens at the top level: **Home** and **Settings**. Home is the only
entry point - device identity, Shizuku status, Show / Hide - and the gear in
its top bar opens Settings. Every Settings category is its own screen (stack
navigation, back arrow), and Lock screen / Debug each hold two sub-screens.

- **Setup checklist** -- first launch walks through Shizuku, overlay, notification access, calendar, notifications and usage access (re-openable from Settings > About).
- **Show / hide from Home** -- Home opens with Show custom bar / Hide custom bar actions, no need to dig into Settings.
- **Custom status bar overlay** -- a Compose bar with clock, date, battery, connectivity, notification and media widgets. Stays visible over the **lock screen** (toggleable) and shows over the system bar while it is hidden.
- **Auto-start bar on boot** -- optional switch (Settings > General): brings the custom bar back after every reboot on its own, without Shizuku and independently of "Auto-hide after reboot".
- **Lock screen modes** (Settings > Lock screen > Appearance) -- "Full" keeps every widget above the keyguard, "Clock only" draws strictly the clock.
- **Lock screen overlay (accessibility)** (Settings > Lock screen > Behavior) -- the keyguard window is hosted by an accessibility service. The screen has an *Enable lock-screen overlay (accessibility)* action that turns it on, and a *Disable* one that turns it off again; the disable only drops our component, so any other accessibility service you use stays on. "Hide notification contents on lock screen" blanks icons and strips progress so the bar leaks no content while locked.
- **Hide the bar inside selected apps** (Settings > Hidden apps) -- a blacklist (usage access) suppresses the overlay while a chosen app is in the foreground; polling every 2 s keeps the transition quick without a background service.
- **SystemUI restart watcher** -- detects a SystemUI restart / crash by PID and re-applies the hide commands so the status bar does not come back (up to 60 s delay).
- **Control API + widget** -- Tasker/automation broadcasts and a home-screen widget toggle the bar or the system bar (see [Control API](control-api.md)).
- **Backup / restore** (Settings > Backup) -- exports all settings and the OEM pick as a JSON file and re-imports it through the system file picker; an import past 64 KiB is refused while reading, before the file is buffered. When both builds are installed (debug + release), the screen also offers *Import from other build*: it detects the sister package and pulls its settings directly, behind a confirmation.
- **Test tools** -- "Send test notification" (Settings > Notifications) posts a 42% progress notification (drives the notification widget and the camera ring), and "Preview camera ring" (Settings > Cutout) draws a full circle around the detected cutout so the gap / position / stroke sliders can be tuned live. The preview is a test mode: it never persists and disappears on restart.
- **Auto OEM detection** (Settings > Device / OEM) -- matches system properties against available JSON configs on first launch; override or re-detect from the UI.
- **Untested badge** -- configs that were only researched online are labelled `untested` on Home; their troubleshooting notes are printed in the log before the commands run.
- **Boot auto-hide** -- a `BOOT_COMPLETED` receiver re-applies the hide commands on reboot (requires "Start on boot" enabled in Shizuku settings). Disable it with the "Auto-hide after reboot" switch (Settings > General).
- **Debug device info** (Settings > Debug > Device info) -- manufacturer, model, Android / SDK, build type, app version and the active OEM config.
- **Debug permissions board** (Settings > Debug > Permissions) -- every grant the app relies on (Shizuku, overlay, notification access, calendar, notifications, usage access, lock-screen accessibility) with an **allowed / blocked** status and a Grant shortcut.
- **Export / share logs** (Settings > Logs) -- copies the log to the clipboard, or shares it as a file (the last 500 timestamped lines are kept on disk, including boot-time output).
- **Log panel** (Settings > Logs) -- shows the output of every command for debugging.
- **Profiles** (Settings > Profiles) -- placeholder for the future profiles feature: save different settings sets and switch between them in one tap.
