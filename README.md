# StatusBar Hider

A minimal Android app that hides the status bar system-wide (including the launcher) using [Shizuku](https://shizuku.rikka.app/). No system apps are uninstalled. Swipe-down to expand notifications is preserved.

The app reads OEM-specific commands from JSON files in `app/src/main/assets/oem/`. Currently supported: **HyperOS** (Xiaomi/Redmi). See [CONTRIBUTING.md](CONTRIBUTING.md) to add more.

## Prerequisites

- A device running a supported OEM skin
- [Shizuku](https://shizuku.rikka.app/) installed and running on the device
  - Start via `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
  - Or pair wirelessly; enable auto-start on boot if needed

## Quick start

```bash
mise install            # install Java 21, Gradle 8.8, Android SDK
mise run install        # build + install release APK on device
```

## Mise tasks

| Task                      | Description                      |
|---------------------------|----------------------------------|
| `mise run build`          | Build signed release APK         |
| `mise run debug`          | Build debug APK                  |
| `mise run install`        | Build + install release APK      |
| `mise run install-debug`  | Build + install debug APK        |
| `mise run clean`          | Clean Gradle outputs             |
| `mise run devices`        | List connected ADB devices       |
| `mise run logcat`         | Stream logcat for StatusBarHider |
| `mise run show-in-drawer` | Re-enable the launcher icon      |
| `mise run uninstall`      | Uninstall the app from device    |

## Build without mise

```bash
gradle assembleRelease
```

Requires `ANDROID_HOME` to be set and the following SDK components installed:

- `platforms;android-35`
- `build-tools;34.0.0` (or higher)

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

All commands are OEM-specific. The JSON files in `app/src/main/assets/oem/` define which commands to run. The app auto-detects the OEM from `Build.MANUFACTURER`/`BRAND`/`MODEL`/`DISPLAY` and saves the selection in SharedPreferences. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to discover commands on a new device.

## App features

- **Auto OEM detection** -- matches system properties against available JSON configs on first launch.
- **Hide from launcher** -- disables the launcher activity so the app disappears from the app drawer. Re-access via `mise run show-in-drawer` or Settings > Apps > StatusBar Hider.
- **Boot auto-hide** -- a `BOOT_COMPLETED` receiver re-applies the hide commands on reboot (requires "Start on boot" enabled in Shizuku settings).
- **Export logs** -- copies the log output to the clipboard.
- **Log panel** -- shows the output of every command for debugging.

## Known limitations

- Disable flags (`cmd statusbar send-disable-flag`) are volatile and reset on reboot / SystemUI restart. The `BOOT_COMPLETED` receiver handles this automatically, but **Shizuku must be configured to start on boot**.
- `ShizukuCmd` calls `IShizukuService.newProcess()` directly via the `aidl` artifact instead of the deprecated `Shizuku.newProcess()` method (private since Shizuku 13.1.5). Stdout is read via `ParcelFileDescriptor.AutoCloseInputStream` with a timeout to avoid pipe deadlocks.

## Project structure

```
StatusBar-Hider/
├── app/src/main/
│   ├── assets/oem/                    # OEM command definitions (JSON)
│   │   └── hyperos.json
│   ├── kotlin/.../
│   │   ├── MainActivity.kt            # ComponentActivity + setContent
│   │   ├── OemConfig.kt               # JSON loader + detection + data classes
│   │   ├── ShizukuCmd.kt              # Shell command runner via Shizuku AIDL
│   │   ├── BootReceiver.kt            # Auto-hide on BOOT_COMPLETED
│   │   ├── ShowInDrawerReceiver.kt    # Re-enable launcher via broadcast
│   │   ├── data/                      # Repositories (prefs/Shizuku/OEM) + CommandRunner
│   │   ├── ui/                        # MainViewModel, MainScreen, sections, theme
│   │   └── overlay/                   # Overlay service + OverlayBar composables + prefs
│   ├── res/
│   │   ├── drawable/                  # Overlay widget icons
│   │   └── values/themes.xml          # Framework host theme (Compose draws its own)
│   └── AndroidManifest.xml
├── keystore/                           # Release signing keystore (gitignored)
├── keystore.properties                 # Signing passwords (gitignored)
├── mise.toml                           # Dev toolchain + tasks
├── CONTRIBUTING.md                     # How to add a new OEM
└── README.md
```

## Credits

Inspired by [Essentials](https://github.com/sameerasw/essentials) by Sameera Perera and [SystemUI Tuner](https://github.com/zacharee/Tweaker) by Zachary Wander.

## License

This project is provided as-is for educational purposes. Use at your own risk.
