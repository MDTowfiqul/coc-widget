# CoC Widget

A real Android home-screen widget for two Clash of Clans accounts. Both
accounts are stacked in **one widget**: each shows its upgrades in progress
(BUILDERS / LAB / HEROES / PETS) with live countdowns, a "Helpers ready in"
line, and an "Exported …" line — the same look as the reference tracker
widget (dark brown card, gold section headers, white countdown text).

No network, no API key, no account login. The widget reads only the village
JSON you paste in the app (the in-game "export village as text" format).
Your data never leaves the phone.

## Install

1. Download the `coc-widget-debug` artifact from the latest **Build debug
   APK** run (Actions tab), unzip it, and install `app-debug.apk` on the
   phone (allow "Install unknown apps" for the browser/files app once).
2. Long-press the home screen → **Widgets** → find **CoC Widget** → drag it
   onto the home screen. Resize it by long-pressing it.
3. The widget works immediately: it ships with the two bundled exports
   (tags #8G08RLGPJ and #LQG98Y98V, exported 7 Nov 2026).

## Updating the data

The widget does **not** fetch anything by itself — the game has no public
feed for this export format, and the widget has no internet permission.

1. In Clash of Clans, export each village as text (in-game export).
2. Open the **CoC Widget** app, paste the fresh JSON into the matching
   account box, type a display name if you want one (defaults to the tag),
   and tap **Save**.
3. The widget updates at once. **Load bundled defaults** restores the two
   exports that shipped with the app.

## How the countdowns stay live

- Every render recomputes `remaining = timer − (now − export timestamp)`,
  so the countdowns tick without new data.
- The widget re-renders every 15 minutes (WorkManager), on boot, when you
  tap the refresh button on the widget, and when you save in the app.
- Tapping the widget header opens the app.

## What it shows

- **BUILDERS** — home-village buildings and traps with a timer.
- **LAB** — troops, spells and siege machines with a timer.
- **HEROES** — heroes with a timer.
- **PETS** — pets with a timer (the section is hidden when empty).
- Rows show the **target** level (current level + 1) and the remaining
  time as `HH:MM:SS`, or `Xd HH:MM:SS` when over a day.
- There is **no progress bar**: the export carries no total upgrade
  duration, and a bar built from a partial duration table would be wrong
  for new upgrades. The countdown text is exact; a bar would be a guess.
- Builder Base items, decorations, skins and equipment are not shown —
  the widget tracks Home Village upgrades only.

## Build

Push to `main` and GitHub Actions builds the debug APK
(`gradle assembleDebug`, Gradle 8.7, JDK 17, no wrapper needed).
Or build locally with Android Studio / Gradle 8.7 + JDK 17.

## Files

- `app/src/main/java/com/towfiqul/cocwidget/` — MainActivity, widget
  provider + RemoteViews service, WorkManager refresh, boot receiver,
  parser + storage (`WidgetData.kt`).
- `app/src/main/assets/names.json` — the 238 gameplay ID → name mappings
  (built from `mapping.json`); `account1.json` / `account2.json` — the
  bundled exports used until you paste fresh ones.
- `verify_widget.py` — Python mirror of the parser: validates the mapping
  coverage and prints the exact rows the widget shows for both accounts.
