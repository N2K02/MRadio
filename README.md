# MRadio

[![Latest release](https://img.shields.io/github/v/release/N2K02/MRadio?label=download)](https://github.com/N2K02/MRadio/releases/latest) ![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white) ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Live radio from Mauritius and the whole world, on your phone and in the car with **Android Auto**.

*Mauritius radio app for Android and Android Auto: listen live to Radio Plus, Top FM, Radio One, MBC and more online radio stations.*

**[⬇ Download the latest APK](https://github.com/N2K02/MRadio/releases/latest)** · Android 8.0 or newer · Free and open source

- **16 Mauritius stations built in**: Radio Plus (+ Hits, Fever, Indiz), Top FM, NRJ Maurice, Radio One, MBC Radio Maurice, Kool FM, Best FM, Taal FM, Radio Mauritius, OUi Radio, Tamizen, Radio 7 and Sensuelle Radio.
- **Three sources, one list**: the built-in stations, the free [Radio Browser](https://www.radio-browser.info/) directory and [Streema](https://streema.com/). The same station from several sources shows once: its first link is the main one and the others become fallbacks. Stations found only on Streema are checked before they are shown, so search only lists radios that play.
- **Country filter**: Mauritius, Worldwide, or any country (searchable list with flags). It applies to search and to the "More from …" list.
- **Favourite Radios**: tap the **heart** on any station (in lists, the player bar, the full player or the notification) to add it. Inside Favourite Radios, **−** takes it out.
- **Stations that stop working** get an **info icon**. It explains what happened and offers *Try again*, *Remove from Favourite Radios* or *Remove completely* (removed stations can be restored at the bottom of the list).
- **Full player**: tap the bar at the bottom. Shows the station's country, location, frequency, genres, language, sound quality (codec, bitrate, sample rate), website and description, plus a **sleep timer**. The arrow at the top makes it small again.
- **Fallback streams**: each station has several stream links, best first. If one fails, the next is tried automatically. If they all fail, MRadio looks up backup links on Radio Browser and Streema. The link that worked is remembered and tried first next time.
- **Android Auto**: Favourite Radios and More from Mauritius folders, search, voice ("Play Top FM on MRadio"), and resume of the last station.
- Plays in the background with the usual media notification, lock-screen and Bluetooth controls; shows the song title when the station sends it.
- Material 3 Expressive (same theme as NetSpeed, VGS and PixelCast): your wallpaper colours, follows the phone's light/dark mode.

## Install

1. Open the latest **Release** of this repo on your phone and download `MRadio-<version>.apk` (e.g. `MRadio-1.0.apk`).
2. Install it (allow "install unknown apps" for your browser if asked).

### Android Auto

Apps installed outside the Play Store are hidden in Android Auto until you allow them once:

1. On the phone open **Android Auto settings** (Settings → Connected devices → Android Auto).
2. Tap **Version** at the bottom about 10 times to turn on developer settings.
3. Menu (⋮) → **Developer settings** → turn on **Unknown sources**.
4. Back in Android Auto settings → **Customise launcher**, and make sure **MRadio** is ticked.

## Adding or fixing a built-in station

Built-in stations live in `BuiltInStations` in `app/src/main/java/com/mradio/app/Station.kt`. Each station has a list of stream URLs, **best first**; the rest are fallbacks.

## Build

Every push to `main` builds the APK with GitHub Actions and publishes it as a release named "MRadio <version>" (1.0, 1.1, 1.2 …, one decimal place) with release notes listing the changes since the previous release. To jump to a new number (e.g. 2.0), run the workflow by hand from the Actions tab and type the version. Locally: `./gradlew assembleRelease` (needs JDK 17 + Android SDK).

## License

MIT. See [LICENSE](LICENSE).
