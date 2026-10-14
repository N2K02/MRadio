# MRadio

Live radio from all around Mauritius, on your phone and in the car with **Android Auto**.

- **16 Mauritius stations built in**: Radio Plus (+ Hits, Fever, Indiz), Top FM, NRJ Maurice, Radio One, MBC Radio Maurice, Kool FM, Best FM, Taal FM, Radio Mauritius, OUi Radio, Tamizen, Radio 7 and Sensuelle Radio.
- **Search** your stations, or find new ones in the free [Radio Browser](https://www.radio-browser.info/) directory (Mauritius or worldwide) and add them with one tap.
- **More from Mauritius**: stations in the directory you haven't added yet are listed under your own.
- **Favourites** at the top of the list (and in Android Auto).
- **Fallback streams**: each station has several stream links, best first. If one fails, the next is tried automatically. If they all fail, MRadio looks up backup links for that station online. The link that worked is remembered and tried first next time.
- **Android Auto**: Favourites, All stations and More from Mauritius folders, search, voice ("Play Top FM on MRadio"), and resume of the last station.
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
