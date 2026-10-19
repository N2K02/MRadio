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

## Updates

The app updates itself from this repo's Releases:

- When you open it (at most every 12 hours) it checks for a newer release. If there is one, a card shows **what's new** with **Update now**, **Later** or **Skip this version**.
- **Check for updates** is at the bottom of the main screen, next to the version number.
- The APK is downloaded inside the app, checked (size, SHA-256 checksum from GitHub, same app, newer version, same signing key) and installed with Android's own installer.
- The first time, Android asks you to allow installs from this app ("Install unknown apps"); after that you just confirm the update.

## Signing

Releases are signed with a private key that is kept encrypted in `signing/keys.tar.enc` and unlocked during the build with the `SIGNING_PASSPHRASE` repository secret (see [signing/README.md](signing/README.md)). Earlier builds used a public test key; Android's key rotation moves installed apps to the private key on the next update, without reinstalling.

## Build

Every push to `main` builds the APK with GitHub Actions and publishes it as a release named "MRadio <version>" (1.0, 1.1, 1.2 …, one decimal place) with release notes listing the changes since the previous release. To jump to a new number (e.g. 2.0), run the workflow by hand from the Actions tab and type the version. Locally: `./gradlew assembleRelease` (needs JDK 17 + Android SDK).

## License

MIT. See [LICENSE](LICENSE).


## Official download and project links

Mauritius radio streaming app for Android and Android Auto with local radio stations, favourites, and playback controls.

- **Latest Android APK:** [Download the latest release](https://github.com/N2K02/MRadio/releases/latest)
- **All releases and version history:** [GitHub Releases](https://github.com/N2K02/MRadio/releases)
- **Source code and issue reporting:** [MRadio repository](https://github.com/N2K02/MRadio)

The APK is distributed through GitHub Releases. If no release is available, check the repository's Releases page for future published builds. Google indexing is not guaranteed; this section provides clear crawlable links for visitors and search engines.
