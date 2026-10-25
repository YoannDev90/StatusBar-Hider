# StatusBar Hider

![No internet](https://img.shields.io/badge/no-internet-brightgreen?style=for-the-badge)
![CI](https://img.shields.io/github/actions/workflow/status/Yoanndev90/StatusBar-Hider/ci.yml?branch=master&style=for-the-badge)
![Latest release](https://img.shields.io/github/v/release/Yoanndev90/StatusBar-Hider?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/language-kotlin-7F52FF?style=for-the-badge)
![minSdk](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-orange?style=for-the-badge)
![Requires Shizuku](https://img.shields.io/badge/requires-Shizuku-2E7D32?style=for-the-badge)
![Shizuku API](https://img.shields.io/badge/Shizuku%20API-13.1.5-4A6572?style=for-the-badge)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Yoanndev90/StatusBar-Hider/total?style=for-the-badge)
![GitHub repo size](https://img.shields.io/github/repo-size/Yoanndev90/StatusBar-Hider?style=for-the-badge)

Hide the Android status bar system-wide — launcher included — using
[Shizuku](https://shizuku.rikka.app/). No system app is ever uninstalled, and
swiping down to expand notifications keeps working.

**Highlights**

- Custom Compose status bar: clock, date, battery, connectivity, notifications, media
- Drawn over the lock screen, with a strict *Clock only* mode
- Comes back hidden after a reboot, on its own
- Backup / restore, home-screen widget, Tasker-friendly control API
- Per-OEM shell commands, auto-detected on first launch

## Why "Corniche"?

*Corniche* is French for the ledge or molding that runs along the very top of
a building — the highest edge of a facade (it also names the coastal road
carved along the top of a cliff). That is exactly where this app works: the
status bar lives on the topmost edge of your screen, and Corniche replaces it
with your own bar along that same edge.

The name is also a nod to the fact that the app grew well beyond hiding the
system bar — it is now mostly a custom status bar (widgets, lock screen,
profiles, control API), and the hide/restore commands are just the
lever underneath. The launcher label changed from *StatusBar Hider* to
*Corniche*; the package name, repository and control API are unchanged.

## Requirements

- A device running a supported OEM skin
- [Shizuku](https://shizuku.rikka.app/) installed and running

  ```bash
  adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
  ```

  Or pair it wirelessly, and enable auto-start on boot if needed.

## Quick start

```bash
mise install        # dev tooling (JDK 21, pre-commit, ktlint)
mise run install    # build + install the release APK
```

You also need an **Android SDK** (`ANDROID_HOME`). The **JDK 21** comes with
`mise install` (`java = "21"` in `mise.toml`) — no separate install.

Everything else — every `mise` task, plain Gradle, the release keystore,
debug vs release builds — is in [Build & signing](docs/building.md).

## OEM configs

Commands live as JSON files in `app/src/main/assets/oem/`.

- **Tested** — HyperOS / MIUI
- **Researched** — Samsung One UI, Google Pixel, OnePlus, OPPO, realme, vivo/iQOO,
  Motorola, Nothing, Huawei, Honor, Sony, ASUS, Nokia, Tecno, Infinix, ZTE/nubia
- **Fallback** — generic AOSP

Everything except HyperOS is flagged `untested` in the UI. See
[CONTRIBUTING.md](CONTRIBUTING.md) to fix a config and promote it.

## Documentation

- [App features](docs/features.md) — everything the app ships today
- [How it works](docs/how-it-works.md) — the Shizuku flow and OEM detection
- [Build & signing](docs/building.md) — mise tasks, Gradle, keystore
- [Control API](docs/control-api.md) — Tasker, automations, shell, widget
- [Known limitations](docs/limitations.md) — volatile flags, polling delays
- [CONTRIBUTING.md](CONTRIBUTING.md) — dev setup and conventions

## Credits

Inspired by [Essentials](https://github.com/sameerasw/essentials) by Sameera
Perera and [SystemUI Tuner](https://github.com/zacharee/Tweaker) by Zachary
Wander.

## Contributors

[![Contributors](https://contrib.rocks/image?repo=Yoanndev90/StatusBar-Hider)](https://github.com/Yoanndev90/StatusBar-Hider/graphs/contributors)

## License

[MIT](LICENSE) — see [LICENSE](LICENSE) for the full text.

This project is provided as-is for educational purposes. Use at your own risk.
