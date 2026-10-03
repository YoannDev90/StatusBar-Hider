# Contributing — Adding a new OEM

StatusBar Hider works by running `adb shell` commands via Shizuku.
Each Android OEM (Xiaomi HyperOS, Samsung One UI, OnePlus OxygenOS, etc.)
uses different system settings and service commands to control the status bar.

This file explains how to discover those commands on a new device
and turn them into an OEM JSON file the app can load.

---

## Step 1 — Prerequisites

- A device running the target OEM skin (e.g. Samsung Galaxy with One UI 7)
- USB debugging enabled + `adb` accessible from a PC
- [Shizuku](https://shizuku.rikka.app/) installed and running on the device
- A terminal where you can run `adb shell` commands

## Step 2 — Discover the hide commands

You need to find **three things**:

### 2a. Immersive / policy_control

```bash
# Try the standard Android immersive mode setting:
adb shell settings put global policy_control 'immersive.status=*'

# Check if it worked (status bar should hide on all screens):
adb shell settings get global policy_control
# Expected output: immersive.status=*

# On some OEMs this key is different:
#   - Samsung:   immersive.mode.confirmation  (boolean, toggles the prompt)
#   - OnePlus:   op_status_bar_policy_control
# If the standard key does not work, search the Settings database:
adb shell settings list global | grep -i status
adb shell settings list secure | grep -i status
```

### 2b. Icon blacklist

```bash
# List all current status bar icon slots:
adb shell settings get secure icon_blacklist

# The value is a comma-separated list of slot names.
# To find them all on your device:
adb shell dumpsys statusbar | grep 'mSlot'

# Try hiding icons by setting a known-good list (e.g. the HyperOS 74-slot list).
# Build your list by adding slot names one by one and checking the result.
# On Samsung/OneUI, the setting might be called:
#   - icon_blacklist  (same key, different slot names)
#   - status_bar_icons
```

### 2c. Disable flags (cmd statusbar)

```bash
# List available flags:
adb shell cmd statusbar send-disable-flag --help
# or:
adb shell cmd statusbar help

# Common flags:
#   system-icons          — system icons group
#   notification-icons    — notification icons
#   clock                 — status bar clock
#   quick-alarm           — alarm icon

# Test:
adb shell cmd statusbar send-disable-flag system-icons clock notification-icons

# To verify:
adb shell dumpsys statusbar | grep -m1 mDisabled1
# Non-zero value = flags are active

# To undo:
adb shell cmd statusbar send-disable-flag none
```

## Step 3 — Discover the restore commands

For each hide command, find its undo:

| Hide command | Restore command |
|---|---|
| `settings put global policy_control 'immersive.status=*'` | `settings delete global policy_control` |
| `settings put secure icon_blacklist ...` | `settings delete secure icon_blacklist` |
| `cmd statusbar send-disable-flag X Y Z` | `cmd statusbar send-disable-flag none` |

If the key is different on your OEM, use the corresponding delete/unset command.

## Step 4 — Discover the status-check commands

| What to check | Command |
|---|---|
| policy_control value | `settings get global policy_control` |
| icon_blacklist size | `settings get secure icon_blacklist \| wc -c` |
| disable flags state | `dumpsys statusbar \| grep -m1 mDisabled1` |

## Step 5 — Create the JSON file

Copy `assets/oem/hyperos.json` as a template:

```bash
cp app/src/main/assets/oem/hyperos.json app/src/main/assets/oem/yourdevice.json
```

Edit the file. The structure is:

```json
{
  "name": "One UI",
  "untested": true,
  "match": ["samsung", "galaxy"],
  "notes": [
    "If the bar does not hide: `settings get global policy_control` returns null -> this key is ignored, rely on the other two commands."
  ],
  "hide": [
    {
      "name": "policy_control",
      "cmd": "settings put global policy_control 'immersive.status=*'",
      "description": "Enable immersive mode globally",
      "persistent": true
    },
    {
      "name": "icon_blacklist",
      "cmd": "settings put secure icon_blacklist slot1,slot2,...",
      "description": "Hide N status bar icon slots",
      "persistent": true
    },
    {
      "name": "disable_flags",
      "cmd": "cmd statusbar send-disable-flag system-icons clock notification-icons",
      "description": "Suppress icon groups",
      "persistent": false
    }
  ],
  "restore": [
    { "name": "policy_control", "cmd": "settings delete global policy_control" },
    { "name": "icon_blacklist", "cmd": "settings delete secure icon_blacklist" },
    { "name": "disable_flags", "cmd": "cmd statusbar send-disable-flag none" }
  ],
  "status": [
    { "name": "policy_control",     "cmd": "settings get global policy_control" },
    { "name": "icon_blacklist_size", "cmd": "settings get secure icon_blacklist | wc -c" },
    { "name": "mDisabled1",         "cmd": "dumpsys statusbar | grep -m1 mDisabled1" }
  ]
}
```

### Field reference

| Field | Required | Description |
|---|---|---|
| `name` | yes | Human-readable OEM name (shown in the UI and used for auto-detection) |
| `untested` | no | `true` = commands were researched but never verified on a device. The UI shows an "untested" badge and the log prints a warning. Remove the flag (or set `false`) once someone tested it. Default: `false` |
| `match` | no | Extra lowercase substrings checked against `Build.MANUFACTURER`/`BRAND`/`MODEL`/`PRODUCT`/`DISPLAY` during auto-detection (e.g. `["xiaomi", "redmi"]` for `hyperos.json`) |
| `notes` | no | Short troubleshooting hints ("if it doesn't work, do X"). Logged in the app before the hide commands run |
| `hide[].name` | yes | Short identifier (shown in logs) |
| `hide[].cmd` | yes | Full shell command to run via Shizuku |
| `hide[].description` | no | What this command does |
| `hide[].persistent` | no | Documentation-only flag: `true` = the setting survives a reboot, `false` = it is lost (default: `true`). The app currently runs every command regardless - the boot receiver re-applies all of them |
| `restore[].name` | yes | Must match the corresponding `hide[]` name |
| `restore[].cmd` | yes | Command to undo the hide |
| `status[].name` | yes | Short identifier for the status field |
| `status[].cmd` | yes | Command that returns the current value |

The app auto-detects the OEM in three steps: JSON file name substring >
`match` substrings > fallback to `aosp.json` (generic defaults). The result is saved in
SharedPreferences, so the next launch skips detection; the **Config** dropdown under
`STATUS BAR` overrides it and **Re-detect OEM** forgets the saved pick and runs detection
again (a saved id that no longer exists in `assets/oem/` is re-detected automatically).

### Untested configs

Most OEM files ship as **untested defaults**: the standard AOSP trio
(`policy_control` + `icon_blacklist` + `cmd statusbar send-disable-flag`) plus researched
OEM notes. To promote a config to tested:

1. Run it on the target device (Step 6).
2. Fix the commands / slot names that don't apply.
3. Set `"untested": false` (or drop the field) and note the verified device in `notes`.

## Step 6 — Test

1. Build the app: `mise run build`
2. Install on the target device: `mise run install`
3. Open the app. The subtitle should show your OEM name.
4. Tap each button and verify the log output matches expectations.
5. **Restore** the status bar before switching devices.

## Step 7 — Submit a PR

- Keep one JSON file per OEM in `app/src/main/assets/oem/`.
- File name = lowercase slug: `oneui.json`, `hyperos.json`, `stock.json`, etc.
- Auto-detection must work: the file name should match a substring of the device's `Build.MANUFACTURER`, `BRAND`, `MODEL`, `PRODUCT`, or `DISPLAY`, otherwise add the substrings to the `match` array.
- New configs start with `"untested": true` until someone verifies them on hardware.
- If a command requires a specific Android version, mention it in `description`.

---

## Quick reference: how to find hidden settings

```bash
# Dump ALL secure settings (huge output):
adb shell settings list secure > settings_secure.txt

# Dump ALL global settings:
adb shell settings list global > settings_global.txt

# Search for status-bar-related keys:
grep -i "status_bar\|icon_blacklist\|immersive\|policy_control" settings_*.txt

# Dump the SystemUI service state:
adb shell dumpsys statusbar

# List all ShellService commands:
adb shell cmd -l
```
