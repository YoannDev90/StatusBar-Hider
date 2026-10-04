# StatusBar Hider

A minimal Android app that hides the status bar system-wide (including the launcher) using [Shizuku](https://shizuku.rikka.app/). No system apps are uninstalled. Swipe-down to expand notifications is preserved.

The app reads OEM-specific commands from JSON files in `app/src/main/assets/oem/`. Supported configs: **HyperOS/MIUI** (tested), plus researched defaults for **Samsung One UI, Google Pixel, OnePlus, OPPO, realme, vivo/iQOO, Motorola, Nothing, Huawei, Honor, Sony, ASUS, Nokia, Tecno, Infinix, ZTE/nubia** and a generic **AOSP** fallback. All of them except HyperOS are flagged `untested` (shown in the UI) - see [CONTRIBUTING.md](CONTRIBUTING.md) to fix and promote one.

## Prerequisites

- A device running a supported OEM skin
- [Shizuku](https://shizuku.rikka.app/) installed and running on the device
  - Start via `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
  - Or pair wirelessly; enable auto-start on boot if needed

## Quick start

```bash
mise install            # install dev tooling (pre-commit, ktlint)
mise run install        # build + install release APK on device
```

Building needs a **JDK 21** and an **Android SDK** (`ANDROID_HOME`) with the
`platforms;android-37.0` and `build-tools;37.0.0` packages - neither is
installed by mise.

## Mise tasks

| Task                      | Description                      |
|---------------------------|----------------------------------|
| `mise run build`          | Build signed release APK         |
| `mise run debug`          | Build debug APK                  |
| `mise run install`        | Build + install release APK      |
| `mise run install-debug`  | Build + install debug APK        |
| `mise run clean`          | Clean Gradle outputs             |
| `mise run devices`        | List connected ADB devices       |
| `mise run logs`           | Live log TUI (logstream)         |
| `mise run uninstall`      | Uninstall the app from device    |

## Build without mise

```bash
./gradlew assembleRelease   # APK
./gradlew testDebugUnitTest # unit tests (prefs, backup, control API)
```

Requires `ANDROID_HOME` to be set and the following SDK components installed:

- `platforms;android-37.0`
- `build-tools;37.0.0` (or higher)

## Signing

Release builds are signed with a keystore generated during setup.
The keystore and its password live in files that are **gitignored**:

```
keystore/statusbarhider.keystore   # PKCS12, RSA 2048, 10 000 days
keystore.properties                 # storeFile, storePassword, keyAlias, keyPassword
```

To generate a new keystore:

```bash
keytool -genkeypair -v \
  -keystore keystore/statusbarhider.keystore \
  -alias statusbarhider -keyalg RSA -keysize 2048 \
  -validity 10000 -storetype PKCS12 \
  -storepass <password> -keypass <password> \
  -dname "CN=StatusBar Hider, OU=Dev, O=Local, L=Local, ST=Local, C=FR"
```

Then create `keystore.properties` at the project root:

```properties
storeFile=keystore/statusbarhider.keystore
storePassword=<password>
keyAlias=statusbarhider
keyPassword=<password>
```

## How it works

1. **Authorize Shizuku** -- grants the app shell-level permissions via the Shizuku binder.
2. **Hide status bar** -- runs shell commands defined in the OEM JSON (e.g. immersive mode + icon blacklist + disable flags).
3. **Check state** -- reads back the current values of those settings.
4. **Restore** -- deletes the settings keys and clears the disable flags.

All commands are OEM-specific. The JSON files in `app/src/main/assets/oem/` define which commands to run. The app auto-detects the OEM from `Build.MANUFACTURER`/`BRAND`/`MODEL`/`DISPLAY` on first launch and saves the pick; the **Config** dropdown under `Status bar` overrides it and **Re-detect OEM** re-runs detection. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to discover commands on a new device.

## App features

- **Setup checklist** -- first launch walks through Shizuku, overlay, notification access, calendar, notifications and usage access (re-openable from the Status tab).
- **Show / hide from the first screen** -- the Status tab opens with Show custom bar / Hide custom bar actions, no need to dig into Style.
- **Custom status bar overlay** -- a Compose bar with clock, date, battery, connectivity, notification and media widgets. Stays visible over the **lock screen** (toggleable) and shows over the system bar while it is hidden.
- **Auto-start bar on boot** -- optional switch (Bar tab, Behavior): brings the custom bar back after every reboot on its own, without Shizuku and independently of "Auto-hide after reboot".
- **Lock screen modes** -- "Full" keeps every widget above the keyguard, "Clock only" draws strictly the clock; "Hide notification contents on lock screen" blanks icons and strips progress so the bar leaks no content while locked.
- **Hide the bar inside selected apps** -- a blacklist (usage access) suppresses the overlay while a chosen app is in the foreground; polling every 2 s keeps the transition quick without a background service.
- **SystemUI restart watcher** -- detects a SystemUI restart / crash by PID and re-applies the hide commands so the status bar does not come back (up to 60 s delay).
- **Control API + widget** -- Tasker/automation broadcasts and a home-screen widget toggle the bar or the system bar (see below).
- **Backup / restore** -- exports all settings and the OEM pick as a JSON file and re-imports it through the system file picker.
- **Style test tools** -- "Send test notification" posts a 42% progress notification (drives the notification widget and the camera ring), and "Preview camera ring" draws a full circle around the detected cutout so the gap / position / stroke sliders can be tuned live. The preview is a test mode: it never persists and disappears on restart.
- **Auto OEM detection** -- matches system properties against available JSON configs on first launch; override or re-detect from the UI.
- **Untested badge** -- configs that were only researched online are labelled `untested` in the header; their troubleshooting notes are printed in the log before the commands run.
- **Boot auto-hide** -- a `BOOT_COMPLETED` receiver re-applies the hide commands on reboot (requires "Start on boot" enabled in Shizuku settings). Disable it with the "Auto-hide after reboot" switch.
- **Export / share logs** -- copies the log to the clipboard, or shares it as a file (the last 500 timestamped lines are kept on disk, including boot-time output).
- **Log panel** -- shows the output of every command for debugging.

## Control API (Tasker, automations, shell)

Receivers are exported but never registered as filters, so only **explicit**
broadcasts (targeting the component) reach them.

```bash
# Custom bar
adb shell am broadcast -a dev.yoanndev90.statusbarhider.SHOW_BAR
adb shell am broadcast -a dev.yoanndev90.statusbarhider.HIDE_BAR
adb shell am broadcast -a dev.yoanndev90.statusbarhider.TOGGLE_BAR

# System status bar (Shizuku hide / restore set)
adb shell am broadcast -a dev.yoanndev90.statusbarhider.HIDE_SYSTEM_BAR
adb shell am broadcast -a dev.yoanndev90.statusbarhider.RESTORE_SYSTEM_BAR
adb shell am broadcast -a dev.yoanndev90.statusbarhider.TOGGLE_SYSTEM_BAR
```

In Tasker, use *Send intent* with the same action and
`dev.yoanndev90.statusbarhider` as the package. Every dispatch is written to
the app log. The home-screen widget (add it from the launcher's widget picker)
sends the show/hide and hide/restore intents through the same code path.

## Known limitations

- Disable flags (`cmd statusbar send-disable-flag`) are volatile and reset on reboot / SystemUI restart. The `BOOT_COMPLETED` receiver handles this automatically, but **Shizuku must be configured to start on boot**.
- `ShizukuCmd` calls `IShizukuService.newProcess()` directly via the `aidl` artifact instead of the deprecated `Shizuku.newProcess()` method (private since Shizuku 13.1.5). Stdout and stderr are drained concurrently with a timeout to avoid pipe deadlocks, and the **exit code is honoured** (a failing command is reported as `FAILED`, not silently swallowed).
- The overlay uses `FLAG_SHOW_WHEN_LOCKED` to draw above the keyguard; without the toggle it behaves like a normal overlay window. "Clock only" is strict: the clock widget is the only thing drawn while locked.
- The app blacklist needs **usage access** (Settings > Apps > Special access > Usage access) and polls the foreground package every 2 s while the feature is on; without the grant the feature stays inert and says so in the log.
- The SystemUI watcher polls the SystemUI PID (no system callback exists for it), so a restart can take up to **60 s** to be noticed, and the hide commands are re-applied only while the app is running (the `BOOT_COMPLETED` receiver covers reboots).
- Backup files carry a `schema_version`; a backup written by a newer build is refused instead of being half-applied.
- The camera ring (including the Style-tab preview) can only be drawn around a **detected** cutout; devices without a punch-hole / notch get no ring geometry to place. The preview also requires the custom bar to be visible.

## Credits

Inspired by [Essentials](https://github.com/sameerasw/essentials) by Sameera Perera and [SystemUI Tuner](https://github.com/zacharee/Tweaker) by Zachary Wander.

## License

This project is provided as-is for educational purposes. Use at your own risk.
