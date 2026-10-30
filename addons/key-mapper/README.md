# Remote keys

Maps a remote button to an app from the TV itself: press **Add a remote button**, press the button,
pick the app. A saved row can be changed to another app or removed. It is a normal Android TV app, with
its own key filter, so it works with this repo's launcher and with any other home app.

## Why a separate app

The [launcher's](../../launcher/) `app_keys` map a button before anything else sees it, but changing it
needs a rebuild, a theme push or adb. This add-on keeps its own list on the TV, changed with the remote
alone. The launcher is not modified and does not depend on this app.

Only one key filter should own a button. If the launcher's `HomeKeyService` or `InputKeyService` maps the
same button, both fire and the app can open twice: turn that `app_keys` entry off first. A button that a
vendor app or the firmware itself handles before any accessibility service never arrives here.

## Rows

| Row | What it does |
|---|---|
| **Key helper: on / off** | Whether Android has this app's accessibility service turned on. OK opens Settings, Accessibility, where it is switched on. |
| A mapped row | The button and the app it opens. OK offers **Open another app** or **Do nothing** (removes the mapping). |
| **Add a remote button** | Waits for the next button you press (Back cancels), then asks which app to open. |

Arrows, OK and Back are used by the screen and are not mapped. A scancode is saved when the remote
reports one, since remotes such as Sony's send several app buttons as a single keycode; the keycode is
used otherwise. **The button is learned by the key helper, not by the screen**, so a firmware button such
as Sony's Netflix key is caught before it opens its own app; turn the helper on before adding a button.

## Permissions

| Permission | Why |
|---|---|
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | To see a remote button before the app in front and open the app mapped to it. It asks for key filtering only: no screen content, no window events. Turned on in Settings, Accessibility, or by `install.sh` over adb. |
| Package visibility (`<queries>` for launcher intents) | To list the apps a button can open on Android 11 and later. |

No internet permission, no accounts, no telemetry, no ads. The list is kept in the app's own storage.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root:

```bash
TV_HOST=<tv ip> addons/key-mapper/install.sh
```

It builds, installs, turns the accessibility service on over adb and opens the screen. To build only:
`cd addons/key-mapper && ./gradlew assembleRelease`; the JVM tests run with `./gradlew testReleaseUnitTest`.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own
builds with your own key if you hand them out.

## Limits

- **Android turns an accessibility service off itself.** A force stop of this app, an update, or the TV's
  memory cleaner can take it off the enabled list, and then every mapped button does nothing. The first
  row shows **Key helper: off**; press OK and switch the service back on. `install.sh` turns it back on.
- **A button another app handles first never arrives.** Netflix, YouTube and similar buttons on some
  sets are taken by a vendor service or the firmware; remapping those needs the launcher's `app_keys`
  or a theme, not this app.
- **A disabled app still answers its own button.** On Sony MediaTek sets the Netflix button is a
  firmware global key (`KEYCODE_BUTTON_4` to `com.mediatek.tv.agent`). With Netflix disabled and no
  mapping here, that firmware says the feature is not available. Map the button first; if the firmware
  still answers, its receiver runs before any accessibility filter, and the button can only be put back
  by enabling Netflix.
- **One filter per button.** Two accessibility services that map the same button both open their app.
- **The TV's own keys cannot be added.** Power and the screensaver key are handled by the system before
  an accessibility service, and the screen keeps volume, arrows, OK and Back for itself.

## Tested on

Sony BRAVIA BF1 (Android TV 12), over ADB: the APK installs, the screen runs with the remote, the key
helper row shows **on** after Android binds the service, and the launcher's own `BoostService` stayed
enabled beside it. Mapping a button and opening its app from that button still needs a press on the real
remote: keys injected over ADB do not reach a key filter, so that step is not claimed here.
