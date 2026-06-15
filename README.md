# BlueDeck

An unofficial, open-source Android app for controlling Hyundai and Kia vehicles via the Bluelink / UVO / Kia Connect API — inspired by the iOS app BetterBlue.

## Fork attribution

BlueDeck is a fork of the open-source **BlueBridge for Android** project, originally created by **[Nelwyn99](https://github.com/Nelwyn99)**. This fork is maintained separately at [tracer99/BlueDeck](https://github.com/tracer99/BlueDeck) to evolve features, UX, and release cadence in a different direction. It is not affiliated with the upstream BlueBridge project, Hyundai Motor Company, or Kia Corporation. See [NOTICE](NOTICE), [LICENSE](LICENSE), and [CHANGELOG](CHANGELOG.md) for release history.

## Features

Notice about features: I am a single developer with a Hyundai in Canada. So Hyundai ownsers in Canada are more likely to have a good experience than any other brand in any other region. Because Hyundai has decided to implement completely different APIs for every region with slightly different features and capabilities, this makes it challenging. Please file bug reports in GibHub if you have any issues and I'll try to address them.

- 🔒 **Lock & Unlock** doors remotely
- 🚗 **Remote Start / Stop** engine with full climate pre-configuration
- ❄️ **Climate Control** — temperature, defrost, heated steering wheel, seat heat levels
- 🔋 **EV Support** — battery status, start/stop charging, set AC & DC charge targets
- 📍 **Vehicle Status** — doors, hood, trunk, tires, ignition, odometer
- 🖼️ **Dashboard vehicle images** — automatic supported Hyundai/Kia images plus an optional custom uploaded dashboard image
- 🔔 **Horn & Lights** — panic button, flash lights only
- 🌍 **Multi-region** — USA Hyundai, USA Kia, Canada, Europe, Australia/NZ
- 🎨 **Material 3 UI** with light, dark, and system appearance
- 🏠 **Home screen widgets** — full, battery, lock, unlock, climate, refresh, and compact controls widgets
- 🔐 **Biometric lock** option
- 🔑 **Secure token storage** via DataStore
- 📏 **Unit preferences** — switch temperature between °F/°C and distance between miles/kilometers

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (Android 8.0+)
- A Hyundai Bluelink or Kia Connect account (existing — same login as the official app)

## Setup

1. Clone or unzip this project
2. Open the **`BlueDeck` project root** folder in **Android Studio** (the directory that contains `settings.gradle.kts`, not a parent folder)
3. Wait for Gradle to sync and download dependencies (~2–3 min first time). The repo includes the Gradle wrapper (`gradlew` / `gradlew.bat`); use **File → Sync Project with Gradle Files** if sync does not start automatically.
4. Connect an Android device or start an emulator
5. Select the **BlueDeck** run configuration in the toolbar, then click **Run ▶**
6. Select your **Region & Brand** on the login screen
7. Sign in with your existing Bluelink / Kia Connect email and password

If **Run** is grayed out after a successful build:

- Confirm the run configuration is **BlueDeck** (module `:app`) via the dropdown next to the Run button → **Edit Configurations…**
- Use **File → Sync Project with Gradle Files**, then **File → Invalidate Caches → Invalidate and Restart**
- Check **File → Project Structure → Project** has an Android SDK selected (not “No SDK”)



## Dashboard Image

The dashboard automatically selects a vehicle image for supported Hyundai and Kia models. You can also tap **Custom image** on the dashboard vehicle image card to choose your own image from the device. BlueDeck stores the selected image URI locally and asks Android for persistable read access when the image provider supports it. Use **Reset** on the same card to return to the automatic model image.

## Unit Preferences

In **Settings → Preferences**, BlueDeck supports:

- **Temperature Unit**: Fahrenheit or Celsius
- **Distance Unit**: miles or kilometers
- **Time Zone**: device time zone by default, with manual overrides for UTC and common North American zones

Distance preference affects dashboard range cards, EV charging range, vehicle status range, odometer display, widgets, and Android Auto read-only status views. Vehicle API data is still cached internally in miles where the source API reports miles, then converted only for display.

The time zone preference is used for vehicle-reported timestamps such as tire-pressure reading times. BlueDeck defaults to the device time zone. If the vehicle/API reports tire timestamps in a different zone, choose an override in **Settings → Preferences → Time Zone**.

## Home Screen Widgets

BlueDeck includes several Android home-screen widgets backed by `VehicleWidgetProvider` and small provider subclasses. Each widget reads the cached selected-vehicle snapshot from DataStore. Refresh and command widgets call the same `VehicleRepository` used by the main app, then refresh and cache the latest battery, range, charging, and lock-state data.

Available widget entries:

| Widget | Default size | Purpose |
|--------|--------------|---------|
| BlueDeck Full | 4×2 | Battery, range, lock state, refresh, lock, unlock, and climate |
| BlueDeck Battery | 2×1 | Compact battery, range, and refresh |
| BlueDeck Battery Wide | 3×2 | Larger battery/range view with lock state and refresh |
| BlueDeck Lock | 1×1 | One-tap lock |
| BlueDeck Unlock | 1×1 | One-tap unlock |
| BlueDeck Climate | 1×1 | One-tap cabin climate using the app default temperature |
| BlueDeck Refresh | 1×1 | One-tap status refresh |
| BlueDeck Lock Controls | 2×1 | Compact lock and unlock buttons |

Most launchers also allow these widgets to be resized after placement, within Android launcher constraints.

### Widget Behavior

- Widgets use the currently selected vehicle from the main app.
- Battery and lock-state information is cached so widgets can render quickly without opening the app.
- Refresh updates the cached vehicle snapshot.
- Command widgets execute immediately through the same repository layer used by the in-app controls.
- After a command completes, widgets request a status refresh so the displayed state can catch up with the vehicle.
- Vehicle API status updates may be delayed by Hyundai/Kia servers, so lock, climate, and charging state can briefly lag behind the command result.

### Widget Stability Notes

The home-screen widgets use only Android `RemoteViews`-compatible view classes. Earlier widget layouts used `Space` separators, which some launchers reject while inflating widgets and display as a generic “Problem loading widget” or error tile. Separators are now implemented as empty `TextView` elements, and widget provider metadata no longer marks widgets as reconfigurable because no configuration activity is supplied.

BlueDeck registers multiple widget entries so the launcher can offer separate battery, refresh, lock, unlock, climate, compact controls, and full-size widgets.

### Widget Size and Dashboard-Style Updates

- The single-action widgets — Lock, Unlock, Climate, and Refresh — advertise Android's legacy 1×1 widget footprint using 40dp minimum width/height plus `targetCellWidth="1"` and `targetCellHeight="1"`.
- The compact Battery widget advertises a 2×1 footprint.
- The wide Battery widget remains a larger 3×2 option.
- Battery widgets use a dashboard-inspired dark blue gradient card, rounded inner status panel, large battery percentage typography, and a blue/green rounded progress bar matching the dashboard battery panel style.

### Climate Command Empty-Response Fix

Some Hyundai/Bluelink remote climate endpoints can return HTTP success with an empty response body. BlueDeck treats successful empty responses as successful commands instead of letting Retrofit/Gson try to parse an empty body as JSON, which previously produced `End of input at line 1 column 1 path $` after climate start/stop succeeded.


### Widget refresh behavior

Widget refresh buttons send a background status-refresh command through `VehicleWidgetProvider`. The widgets now show transient messages such as `Refreshing…` or `Sending lock…` immediately after a tap so it is clearer that the button press was received. Full-size widget controls also use larger touch targets for easier tapping.

## First-Run Region Selection

BlueDeck lets users choose their **Region & Brand** directly on the login screen before authentication. This is important because Hyundai/Kia accounts are region-specific and use different services in the USA, Canada, Europe, and Australia/NZ. The selected region is saved locally and is also available later under **Settings → Region & Brand**.


## Europe Vehicle Support

European Hyundai/Kia/Genesis support uses the EU CCSP API (`prd.eu-ccapi.*`) rather than the USA or Canada API paths. Because the EU login pages currently rely on browser/reCAPTCHA authentication, BlueDeck's EU login expects a valid 48-character EU refresh token in the password field instead of the normal account password.

Implemented EU features are experimental and include:

- Refresh-token based sign-in
- Vehicle list
- Cached and live status refresh
- Lock / unlock through the EU control-token flow when a PIN is stored
- Climate start / stop for supported vehicles
- EV charge start / stop for supported vehicles

Known EU limitations:

- The app does not yet generate the EU refresh token internally.
- Horn/lights and charge-target editing are not mapped yet.
- Location parsing is not surfaced in the BlueDeck UI model yet.
- Endpoint behavior can vary between Hyundai, Kia, Genesis, and CCS2-capable vehicles.

## Unit and Time Preferences

BlueDeck lets you choose Fahrenheit/Celsius, miles/kilometers, a display time zone, and 12-hour or 24-hour timestamp formatting. Vehicle-reported tire and odometer reading times use the selected time-zone and time-format preferences for consistent display.

## Regional Configuration

In the app go to **Settings → Region & Brand** and select:

| Option | Use for |
|--------|---------|
| USA — Hyundai | US Bluelink accounts |
| USA — Kia | US Kia Connect / UVO accounts |
| Canada — Hyundai | Canadian Bluelink via `mybluelink.ca` TODS API |
| Canada — Kia | Canadian Kia Connect via `kiaconnect.ca` TODS API |
| Canada — Genesis | Canadian Genesis Connect via `genesisconnect.ca` TODS API |
| Europe — Hyundai | EU Hyundai Bluelink accounts. Uses EU CCSP refresh-token login. |
| Europe — Kia | EU Kia Connect accounts. Uses EU CCSP refresh-token login. |
| Europe — Genesis | EU Genesis accounts. Uses EU CCSP refresh-token login. |
| Australia — Hyundai | Australian Hyundai Bluelink accounts |
| Australia — Kia | Australian Kia Connect accounts |
| New Zealand — Kia | New Zealand Kia Connect accounts |
| Australia / NZ | Legacy/default Australian Hyundai selection |

## Security & Privacy

- BlueDeck is a client app for your existing Hyundai Bluelink or Kia Connect account.
- Credentials and tokens are stored locally using the app's DataStore-backed storage layer.
- Biometric lock can be enabled to add an extra local access gate before using the app.
- Vehicle commands are sensitive actions. Treat any device with BlueDeck installed as capable of sending remote vehicle commands.
- This app is not intended for shared or unattended devices unless Android device-level security is enabled.

## Troubleshooting

| Issue | What to try |
|-------|-------------|
| Login fails | Confirm the same credentials work in the official Hyundai/Kia app, then verify the selected region and brand. |
| Vehicle list is empty | Refresh after login and confirm the account has an active enrolled vehicle in the official app. |
| Commands work but status looks stale | Use Refresh. Server-side status can lag behind successful commands. |
| Widget shows no vehicle | Open the app once, sign in, select a vehicle, then add or refresh the widget. |
| Widget command appears delayed | The command may have been accepted while the vehicle status endpoint has not updated yet. Refresh again after a short interval. |
| Climate start/stop shows success but status lags | The remote command and the vehicle status update are separate API flows; status may trail the accepted command. |
| Region-specific features are missing | Some endpoints and features vary by Hyundai/Kia region, vehicle model, account type, and EV vs ICE platform. |


## Canadian Vehicle Support

Canada uses the Hyundai/Kia TODS web API rather than the USA mobile API. BlueDeck includes a separate Canadian API path for:

- Canadian Hyundai: `https://mybluelink.ca/tods/api/`
- Canadian Kia: `https://kiaconnect.ca/tods/api/`
- Canadian Genesis: `https://genesisconnect.ca/tods/api/`

Implemented Canadian flows:

- login through `/v2/login` with full MFA when the server returns error `7110` for a new or untrusted device
- select verification method (email or SMS when available), enter the 6-digit code on the login screen, and optionally trust this device for 90 days
- stable generated `Deviceid` storage (same ID reused across sessions to honor device trust)
- vehicle list through `/vhcllst`
- cached and live status through `/lstvhclsts` and `/rltmvhclsts`
- PIN verification through `/vrfypin` before protected commands
- lock and unlock through `/drlck` and `/drulck`
- cabin climate start/stop through `/rmtstrt`, `/rmtstp`, `/evc/rfon`, and `/evc/rfoff`
- EV charge start/stop through `/evc/rcstrt` and `/evc/rcstp`

### Login verification (2FA) by region

| Region | Device verification at login |
|--------|------------------------------|
| USA — Kia | Email or SMS OTP when Kia requests device trust |
| Canada — Hyundai / Kia / Genesis | Email or SMS MFA for new devices (`7110`) |
| USA — Hyundai | Standard password login (no OTP in current telematics API) |
| Europe | Refresh-token or password login (no device MFA step) |
| Australia / New Zealand | Standard OAuth sign-in (no device MFA step) |

The 4-digit **Bluelink PIN** collected at login is for remote commands (lock, climate, etc.), not login 2FA.

Currently not mapped for Canada: horn/lights, vehicle location, and charge-target editing.

## Australia / New Zealand Vehicle Support

Australia/New Zealand support is experimental and uses the regional CCSP API hosts documented by the public Hyundai-Kia-Connect API work:

- Hyundai Australia: `au-apigw.ccs.hyundai.com.au:8080`
- Kia Australia: `au-apigw.ccs.kia.com.au:8082`
- Kia New Zealand: `au-apigw.ccs.kia.com.au:8082` with the New Zealand Kia service identifiers

Mapped features include login, vehicle list, cached/live vehicle status, lock/unlock attempts, climate start/stop attempts, and EV charge start/stop attempts. Status reads are the most likely to work consistently. Command support can vary by vehicle, account, market, and API permission state, so treat Australian/NZ commands as experimental until verified on real vehicles.

## Architecture

```
app/
└── java/com/bluedeck/
    ├── data/
    │   ├── api/          # Retrofit API service + constants
    │   ├── models/       # All data classes (Vehicle, Status, etc.)
    │   └── repository/   # VehicleRepository + PreferencesManager
    ├── di/               # Hilt dependency injection
    ├── ui/
    │   ├── components/   # Reusable Compose components
    │   ├── navigation/   # NavHost / Screen routes
    │   ├── screens/      # Login, Dashboard, Controls, Status,
    │   │                 # RemoteStart, EVCharging, Settings
    │   └── theme/        # Colors, Typography, Theme
    ├── viewmodel/        # AuthViewModel, VehicleViewModel, SettingsViewModel
    └── widget/           # Home screen widget providers and RemoteViews layouts
```

**Stack:** Kotlin · Jetpack Compose · Hilt · Retrofit · OkHttp · DataStore · Navigation Compose · Android App Widgets

## Build Notes

- Open the project root in Android Studio before building.
- Let Android Studio sync Gradle dependencies before running the app.
- Use a physical device for best testing of widgets, biometrics, and background command behavior.
- Some emulators do not fully match real launcher widget sizing behavior.

## Known Limitations

- Hyundai/Kia APIs are unofficial and may change without notice.
- API availability varies by region, brand, vehicle model, and account state.
- Widgets depend on cached app state and Android launcher behavior.
- Android launchers may render widget spacing and exact grid size differently.
- Remote command status can be delayed even when a command was accepted successfully.
- Vehicle wake-up, network coverage, subscription status, and server-side throttling can affect command reliability.

## Future Ideas

- Push notifications for status changes
- Trip history parsing
- Scheduled remote start timer
- Android Auto companion experience
- Wear OS companion app
- More per-widget configuration options
- Vehicle nickname and multi-vehicle widget selection

## Privacy

See [PRIVACY.md](PRIVACY.md) for details about local storage, connected-car API communication, and what to remove before sharing logs or screenshots.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

## Disclaimer

This app is **not affiliated with Hyundai Motor Company or Kia Corporation**. It communicates with the same API endpoints used by the official apps. Use at your own risk. The authors take no responsibility for any unintended vehicle actions.

### Biometric lock grace period

When biometric lock is enabled, BlueDeck now keeps the current unlock session active during brief app switches or accidental minimizes. The app only requires biometric re-authentication after it has been in the background for more than about five minutes, or after the app process/session is otherwise reset.


### Biometric widget behavior

When biometric lock is enabled, BlueDeck keeps a short unlock grace period after a successful biometric check. Opening the app from a home-screen widget during that grace period should return to the dashboard without asking for fingerprint again. After the grace period expires, widget-launched app opens require biometric unlock again.
