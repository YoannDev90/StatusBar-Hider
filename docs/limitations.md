# Known limitations

- Disable flags (`cmd statusbar send-disable-flag`) are volatile and reset on reboot / SystemUI restart. The `BOOT_COMPLETED` receiver handles this automatically, but **Shizuku must be configured to start on boot**.
- `ShellRunner` calls `IShizukuService.newProcess()` directly via the `aidl` artifact instead of the deprecated `Shizuku.newProcess()` method (private since Shizuku 13.1.5). Stdout and stderr are drained concurrently with a timeout to avoid pipe deadlocks, and the **exit code is honoured** (a failing command is reported as `FAILED`, not silently swallowed).
- The overlay uses `FLAG_SHOW_WHEN_LOCKED` to draw above the keyguard; without the toggle it behaves like a normal overlay window. "Clock only" is strict: the clock widget is the only thing drawn while locked.
- The app blacklist needs **usage access** (Settings > Apps > Special access > Usage access) and polls the foreground package every 2 s while the feature is on; without the grant the feature stays inert and says so in the log.
- The SystemUI watcher polls the SystemUI PID (no system callback exists for it), so a restart can take up to **60 s** to be noticed, and the hide commands are re-applied only while the app is running (the `BOOT_COMPLETED` receiver covers reboots).
- Backup files carry a `schema_version`; a backup written by a newer build is refused instead of being half-applied.
- The camera ring (including the Style-tab preview) can only be drawn around a **detected** cutout; devices without a punch-hole / notch get no ring geometry to place. The preview also requires the custom bar to be visible.
