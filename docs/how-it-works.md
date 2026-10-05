# How it works

1. **Authorize Shizuku** -- grants the app shell-level permissions via the Shizuku binder.
2. **Hide status bar** -- runs shell commands defined in the OEM JSON (e.g. immersive mode + icon blacklist + disable flags).
3. **Check state** -- reads back the current values of those settings.
4. **Restore** -- deletes the settings keys and clears the disable flags.

All commands are OEM-specific. The JSON files in `app/src/main/assets/oem/` define which commands to run. The app auto-detects the OEM from `Build.MANUFACTURER`/`BRAND`/`MODEL`/`DISPLAY` on first launch and saves the pick; the **Config** dropdown under `Status bar` overrides it and **Re-detect OEM** re-runs detection. See [CONTRIBUTING.md](../CONTRIBUTING.md) for how to discover commands on a new device.
