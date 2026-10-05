# Control API (Tasker, automations, shell)

Receivers are exported but never registered as filters, so only **explicit**
broadcasts (targeting the component) reach them -- the component name is
required, an action alone delivers nothing. Every broadcast must also carry
the install's **control token**: copy it from **Status > App > Copy control
API token**, then pass it as the string extra `token`. Without it the
broadcast is dropped before dispatch (the rejection is written to logcat).

```bash
SBH="dev.yoanndev90.statusbarhider/.control.ControlReceiver"
TOKEN="<pasted token>"

# Custom bar
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.SHOW_BAR --es token "$TOKEN"
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.HIDE_BAR --es token "$TOKEN"
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.TOGGLE_BAR --es token "$TOKEN"

# System status bar (Shizuku hide / restore set)
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.HIDE_SYSTEM_BAR --es token "$TOKEN"
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.RESTORE_SYSTEM_BAR --es token "$TOKEN"
adb shell am broadcast -n "$SBH" -a dev.yoanndev90.statusbarhider.TOGGLE_SYSTEM_BAR --es token "$TOKEN"
```

In Tasker, use *Send intent* with the same action, `dev.yoanndev90.statusbarhider`
as the package, `dev.yoanndev90.statusbarhider.control.ControlReceiver` as the
class, and an Extra `token:<TOKEN>` (no quotes). Every dispatch is written to
the app log. The home-screen widget (add it from the launcher's widget picker)
sends the show/hide and hide/restore intents with the token baked into its
PendingIntents, so taps keep working without any setup.

Why a token: Android never tells a manifest receiver who sent a broadcast --
on recent versions `getSentFromUid()` reports `INVALID_UID` for *every*
sender, shell included -- so the sender cannot be identified or allowlisted.
The token is a random secret in app-private prefs, which other apps cannot
read, so `adb`/Tasker/our widget keep working while everything else is
refused. It is deliberately **not** part of the settings export; a backup
restore generates a fresh one, so re-paste it into your automations after
importing a backup on a new device.
