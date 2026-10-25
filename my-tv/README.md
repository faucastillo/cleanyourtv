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

| Button | Scancode (`getevent -lt`) | Works now? |
|---|---|---|
| Netflix | - | Yes (Hisense/MediaTek receivers handle it) |
| Input | - | Yes |
| YouTube | 469 (`KEY_FN_F4`) | No |
| Prime Video | 468 (`KEY_FN_F3`) | No |
| Disney+ | 418 (`KEY_ZOOMIN`) | No |

YouTube, Prime Video and Disney+ were answered by the Google TV launcher. With it disabled they do nothing.
This was a deliberate choice; both ways to get them back were tested on this TV and worked.

### Option 1: key helper (buttons work, volume steps once per press)

```sh
# themes/<name>/config.json
{
  "app_keys": [
    "scan:469:com.google.android.youtube.tv",
    "scan:468:com.amazon.amazonvideo.livingroom",
    "scan:418:com.disney.disneyplus"
  ]
}
TV_HOST=<tv ip> tools/theme.sh themes/<name>
cd launcher && TV_HOST=<tv ip> INPUT_KEY_SERVICE=1 ./build-and-install.sh
```

Any accessibility service that filters keys makes Android 12 drop the volume key's auto-repeat: holding
volume up moves it one step. See [`docs/devices/tcl-55p71b-g09-key-repeat.md`](../docs/devices/tcl-55p71b-g09-key-repeat.md).
Any app package works in place of the stock ones (e.g. TizenTube for the YouTube button).

### Option 2: Google TV launcher in the background (buttons and volume ramp both work)

From [upstream #1292](https://github.com/Gathin23/cleanyourtv/issues/1292). The Google TV launcher stays
enabled so its own button receiver works, and `HomeKeyService` runs **without** key filtering and sends
its home screen back to Clean Your TV whenever it comes up.

```sh
# themes/<name>/config.json
{
  "key_filter": false,
  "stock_home_redirect": "com.google.android.apps.tv.launcherx"
}
TV_HOST=<tv ip> tools/theme.sh themes/<name>
TV_HOST=<tv ip> tools/tv.py "pm enable --user 0 com.google.android.apps.tv.launcherx"
TV_HOST=<tv ip> tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1"
```

Verified here: Home from inside an app and a reboot both land on Clean Your TV, and `dumpsys accessibility`
shows the helper bound with no `KeyboardInterceptor`. Google TV profiles also work again (the chooser opens
with `am start -a com.google.android.gms.account.ProfilePickerDelegation -n
com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity`), but at boot the redirect
covers the profile chooser.

Cost, measured idle: about 87 MB PSS for the two launcherx processes (`launcherx` + `:coreservices`), 0 CPU
ticks over 60 s, and background fetching of recommendations. Free RAM was 559 MB with it, 667 MB without.

Do not use `build-and-install.sh` with option 2 active: it would see launcherx as Home and disable it.

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

- **Frosted glass**: the wallpaper is blurred once per picture at 1/8 size (`Frost`) and the dock band and the cards
  draw the part behind them (`FrostDrawable`). No live blur, so no cost per frame.
- **White dock logos** (`MonoLogo`): made from each app's banner; `themes/<name>/logos/<package>.png` overrides one
  (TizenTube has a hand-made one). `tools/theme.sh` pushes that folder.
- **Photo card** (`PhotoCard`): pictures from `my-photos/` (git-ignored), sent with `tools/photos.sh`, a new one
  every `photo_minutes`.
- **Weather card** (`WeatherCard`, `Weather`, `WeatherIcon`): Open-Meteo for Palermo, CABA, every 30 minutes while
  Home is on screen. **This adds the `INTERNET` permission to the launcher**; `api.open-meteo.com` is the only host
  it contacts. Remove `weather` from the theme to stop it.
- **Quieter header**: 24-hour clock, no date, small photo credit beside the top buttons.

All of it is off unless a theme has `glass_home`; `GlassHome` holds the wiring so upstream files only got small hooks
(`Config.overlay`, `WallpaperPlayer`, `DockDecoration`, `AppAdapter`, `MainActivity`, `ThemeFiles`, `ThemeProvider`).
