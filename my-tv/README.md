# My TV: Hisense 50Q6

Personal setup notes for this fork. Not meant for upstream.

| | |
|---|---|
| Model | Hisense 50Q6 (Android model string `SmartTV 4K FFM`, device `lushan`, MediaTek MT9676) |
| OS | Google TV 12 (SDK 31), firmware `STT2.230929.001.092` |
| RAM | 1.8 GB |
| Closest upstream list | [`examples/hisense-smarttv-4k-ffm-lushan/`](../examples/hisense-smarttv-4k-ffm-lushan/debloat.txt) (same firmware) |

## Current state (2026-10-25)

- Launcher: Clean Your TV (`com.gathin.tvhome`) is Home, installed with plain `launcher/build-and-install.sh`
  (no `INPUT_KEY_SERVICE`, no `HOME_KEY_SERVICE`, no theme).
- Google TV launcher (`com.google.android.apps.tv.launcherx`): **disabled**. No Prime Video row, no
  Google TV profiles chooser, no recommendations fetched in the background.
- No accessibility service bound: holding volume up/down ramps continuously.
- Disabled packages: [`debloat.txt`](debloat.txt), applied in four batches with a screenshot after each and
  a reboot at the end.
- Kept on purpose: all streaming apps (including the sideloaded TizenTube, `io.gh.reisxd.tizentube.cobalt`),
  the Hisense browser, Google Assistant (`katniss`), casting/AirPlay/DLNA, OTA updaters, Play Store and
  Play Services, `com.vt.tv.smartsense*` and `com.vt.tv.aiot` (disabling them sent another FFM board into
  Android Recovery).

Undo everything:

```sh
TV_HOST=<tv ip> tools/restore.sh my-tv/debloat.txt com.google.android.apps.tv.launcherx
```

## Remote buttons

| Button | keycode / scancode | Does now | How |
|---|---|---|---|
| Netflix, Input | - | Netflix, the inputs | the firmware's own receivers |
| Gear | 83 `NOTIFICATION` / 598 | TV Settings | KeyWatcher |
| TV icon (Guide) | 172 `GUIDE` / 362 | Flow's TV guide, from anywhere | KeyWatcher, `content://livetv` deep link |
| YouTube | - / 469 | YouTube | KeyWatcher |
| Prime Video | - / 468 | Prime Video | KeyWatcher |
| Disney+ | - / 418 | Disney+ | KeyWatcher |
| Apps (grid) | - / 580 | TizenTube | KeyWatcher |
| Configurable button | - | Kick | the TV's own setting |

With launcherx disabled these keys are *global keys* addressed to launcherx (`dumpsys window policy`), so they reach
nothing. Upstream's way to catch them (`app_keys` + a key-filtering accessibility helper) makes Android 12 drop the
volume key's auto-repeat, and keeping launcherx in the background costs ~87 MB; both were rejected.

**KeyWatcher** (this fork, theme key `remote_watch`): this firmware logs every key as
`WindowManager: interceptKeyTq keycode=N scancode=M`. The launcher reads that log (`logcat` filtered to that tag and
`wm_set_resumed_activity`, for per-app entries) and opens the mapped target. Nothing is filtered, so volume keeps
ramping and the buttons work inside any app. Cost measured on Home: ~0.05 % of one core, 2.9 MB.
Install with `KEY_WATCH=1 launcher/build-and-install.sh` (grants `READ_LOGS` and the `SYSTEM_ALERT_WINDOW` app op).
The trade: the launcher may read the system log; it only parses those two kinds of lines and keeps nothing.
Flow's guide link opens the guide only once Flow is running, so from a cold start KeyWatcher repeats it after 7 and
14 s unless another button was pressed.

Entries: `"scan:<scancode>:<target>"`, or `"app:<package>:scan:<scancode>:<target>"` to apply only while that app is
in front. Targets: a package, `settings`, `inputs`, `input:<id>`, or `intent:<intent URI>`.

## Known side effects

- Google TV "Continue watching" row is gone. Apps keep their own history and profiles inside the app.
- Google TV profiles cannot be switched (only matters for Google-account-wide things such as Play Store).
- Google Assistant answered with "problems processing the request" once launcherx was disabled. Not used,
  so not investigated.

## Privacy notes

- `tools/privacy.py` found Google Assistant (`katniss`) and `com.google.android.tts` with recorded
  microphone access (expected: the remote's mic button), and flagged `com.vt.alexa` (now disabled).
- Hisense's `com.vt.tv.evservice` (ACR) is disabled.
- `com.vt.tv.smartsense` logs and sends app usage (`LogReportUtils: The APPRUN map sent out`). It cannot be
  disabled safely; network-level DNS blocking is the way to cut that.

## Glass Home (this fork's launcher changes)

Theme [`themes/argentina-glass`](../themes/argentina-glass/) plus launcher code under the theme key `glass_home`:

- **Liquid glass** (`LiquidGlass`): the wallpaper is blurred once per picture at 1/8 size (`Frost`); every glass
  surface (dock band, cards) draws the part behind it, with a lens rim that magnifies the background,
  specular light on the top-left and bottom-right of the edge and a thin inner top line. The focus highlight is the
  same glass without the blur (it sits over the logo). No live blur, so no cost per frame.
- **Top buttons**: only Inputs and Settings (`show_network_pill: false`), in a barely-there glass
  (`LiquidGlass.subtle`): mostly clear, faint rim, brighter only when focused.
- **White dock logos** (`MonoLogo`): `themes/argentina-glass/logos/<package>.png` for TizenTube, Flow, Disney+, Kick,
  YouTube, Netflix, Prime Video, Spotify and HBO Max, made by `logos/make_logos.py` from each app's own TV banner
  (any other app in the dock gets one made on the TV from its banner). Every logo is sized to the same optical
  size (`MonoLogo.normalize`: equal bounding-box area with a gentle correction for how solid the mark is, so a
  block wordmark like Kick is not shrunk next to thin ones). `tools/theme.sh` pushes the folder.
- **TizenTube looks like YouTube** on purpose (guests see YouTube): the YouTube logo with a small play ring as a
  superscript (`TIZENTUBE_AS_YOUTUBE` in `logos/make_logos.py`). Logos from small banners are enlarged with
  smooth edges (`smooth_upscale`) and stored 960 px wide, so the TV only scales them down.
- **Rows below the dock hidden** until you press Down (`hide_grid_until_down`).
- **Photo credits** on screen are off (`show_credits: false`); the credits live in the theme's `CREDITS.md`.
- **Photo card** (`PhotoCard`): pictures from `my-photos/` (git-ignored), sent with `tools/photos.sh`, a new one
  every `photo_minutes`.
- **Weather card** (`WeatherCard`, `Weather`, `WeatherIcon`): Open-Meteo for Palermo, CABA, every 30 minutes while
  Home is on screen. **This adds the `INTERNET` permission to the launcher**; `api.open-meteo.com` is the only host
  it contacts. Remove `weather` from the theme to stop it.
- **Quieter header**: 24-hour clock, no date.

### Reordering apps

- On the TV: focus the gear (Settings) at the top right, **hold OK**, choose **Reorder apps**. OK picks an app up,
  the arrows move it, OK puts it down; Back (with nothing picked up) leaves and saves. The first row of four is the
  dock (white logos on glass); the rows after it are the grid, which shows when you press Down.
- In the theme: the order of `dock_apps` in `themes/argentina-glass/config.json`, then
  `TV_HOST=<tv ip> tools/theme.sh themes/argentina-glass`.

All of it is off unless a theme has `glass_home`; `GlassHome` holds the wiring so upstream files only got small hooks
(`Config.overlay`, `WallpaperPlayer`, `DockDecoration`, `GlassView`, `AppAdapter`, `MainActivity`, `ThemeFiles`,
`ThemeProvider`).
