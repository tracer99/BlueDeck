# Changelog

All notable changes to BlueDeck are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

BlueDeck is a fork of [BlueBridge for Android](https://github.com/Nelwyn99) by Nelwyn99, maintained independently at [tracer99/BlueDeck](https://github.com/tracer99/BlueDeck).

## Versioning policy

- **Bug fixes**: patch bump (`1.4.0` → `1.4.1`) — update `versionName` in `app/build.gradle.kts`, increment `versionCode` by 1, and add a `CHANGELOG.md` entry.
- **Features**: minor bump (`1.4.0` → `1.5.0`) — same files.
- **Major releases** (`x.0.0`): only when explicitly requested.
- **Git**: each feature, plan, or fix is developed on a new branch (`feature/`, `fix/`, or `plan/`), not directly on `main`.

See `.cursor/rules/release-workflow.mdc` for agent workflow details.

## [1.12.1] - 2026-07-20

### Fixed

- Walk-Away Lock: start the monitoring notification only when the selected vehicle Bluetooth connects (including after Android Auto/Bluetooth connect without opening the app), schedule lock on ACL disconnect, and clear the notification after the lock attempt.
- Walk-Away Lock: stop running a forever foreground service while the feature is merely enabled, reducing idle battery use.

### Changed

- Walk-Away Lock triggers on Bluetooth ACL connect/disconnect only (ignores HFP/A2DP profile flaps).
- Settings → Automation: prompts to allow unrestricted battery use and exact alarms when Walk-Away Lock is enabled.

## [1.12.0] - 2026-07-17

### Added

- Remember last-used climate start temperature and duration, and restore them as the dashboard defaults next time.

## [1.11.1] - 2026-07-17

### Fixed

- Frequent unexpected logouts: keep the session on network/transient token-refresh failures instead of wiping tokens.
- US Hyundai: renew access tokens with the OAuth `refresh_token` grant (plus saved credentials) instead of a full password login every ~30 minutes.
- Serialize concurrent token refreshes so rotating refresh tokens are not invalidated by parallel app/widget calls.
- Europe: only treat clear auth failures as session death; retry status fetch after a token refresh before logging out.

## [1.11.0] - 2026-07-17

### Added

- Demo mode for Google Play verification: tap **Try Demo Mode** on the login screen to explore BlueDeck with a fake Hyundai IONIQ 5 and Kia EV6. Remote actions update local mock state and never call Blue Link APIs.

## [1.10.1] - 2026-07-17

### Fixed

- GitHub Release CI: resolve `Properties` in `app/build.gradle.kts` under AGP 9 Kotlin DSL so signed APK builds succeed.
- Release workflow keystore decode: strip whitespace from `KEYSTORE_BASE64` (common Windows paste issue).

## [1.10.0] - 2026-07-17

### Added

- When a remote command needs a Bluelink PIN and none is saved, prompt for the PIN with an optional “Save PIN for future commands” checkbox instead of failing immediately.
- Walk-Away Lock automation: on vehicle Bluetooth disconnect, wait the configured delay, then send a remote lock (with foreground monitoring notification and success/failure alerts).

### Changed

- Moved the dashboard “Recent Commands” log below Features so it sits at the bottom of the screen.
- Settings → Preferences: toggle to show or hide Recent Commands on the dashboard (on by default).

### Fixed

- Walk-Away Lock actually runs end-to-end (Bluetooth receivers, delayed alarm, and lock API were previously empty stubs).
- Climate start: keep the On chip while BlueLink status catches up (was flipping back to Off until a later refresh).
- Climate seat controls stay visible while climate is running (read-only) instead of disappearing.
- Do not assume cooled/ventilated seats when the API does not report them (fixes Cool presets on Canada IONIQ 9 trims without seat ventilation).
- Canada vehicle status: map `airCtrl` → `airCtrlOn` and `seatHeaterVentState` → seat heater fields so climate/seat status parse correctly.
- Climate Presets (renamed from Seat Presets): Manage edits Warm/Cool start presets (temperature, duration, defrost, heated steering, seats); dashboard All Off stops climate instead of applying a start preset.
- Climate preset defaults use round temperatures in the Settings temperature unit (°C or °F).
- Climate Presets and dashboard climate include a run-duration picker (5–30 min); Settings → Preferences sets the default duration for quick actions and the widget.
- Show the Bluelink PIN prompt from the active MainActivity UI (it was previously wired only to an unused nav host, so climate taps appeared to do nothing after confirm).
- Canada IONIQ 9 / EV9-style climate start: retry with `remoteControl` when `hvacInfo` returns error 15109.
- Canada climate stop shortly after start: surface BlueLink’s 90-second “processing earlier request” cooldown clearly, and remember the working climate payload so start doesn’t burn an extra API call.
- EV Climate On/Off chip on the dashboard is interactive again (it was permanently disabled and ignored taps).
- Restored a Climate quick action for EVs alongside Lock/Unlock (parity with ICE Start/Stop).
- OBD Connect / Start logging now requests the Nearby Devices (Bluetooth) permission on Android 12+ before connecting, instead of failing with a permission error.

## [1.9.0] - 2026-07-14

### Changed

- Replaced the placeholder launcher and splash icons with the BlueDeck brand logo (adaptive icon + splash screen).
- Updated Jetpack Compose BOM to 2025.05.01 for the Compose Autofill `ContentType` API.

### Fixed

- Login email and password fields now expose Android Autofill content types so password managers (e.g. 1Password) can offer credentials on focus.

## [1.8.0] - 2026-07-14

### Added

- GitHub Actions release workflow: pushing a `vX.Y.Z` tag builds a signed release APK and publishes it on the GitHub Releases page.
- Optional CI signing via `KEYSTORE_*` / `KEY_*` environment variables (used by the release workflow).

## [1.7.1] - 2026-06-15

### Changed

- Migrated build to Android Gradle Plugin 9 defaults: built-in Kotlin, new DSL, and removal of legacy `gradle.properties` opt-outs.
- Upgraded Kotlin to 2.3.0 and KSP to 2.3.4 for AGP 9 built-in Kotlin compatibility (Hilt/Room annotation processing).

## [1.7.0] - 2026-06-15

### Added

- OBD2 driving logs: pair Bluetooth Classic or Wi-Fi ELM327 scanners and record Hyundai/Kia EV extended PIDs while driving (12V aux, traction SOC/SOH, battery temps, heater state, cell voltages, motor RPM, brake/headlight status when supported).
- OBD diagnostics screen with live metrics, session history, CSV export, and configurable log retention (days and max storage).
- Optional Google Drive sync for OBD session CSV uploads.

## [1.6.5] - 2026-06-14

### Changed

- “Stay logged in for 30 days” moved from Settings to the login screen as a checkbox (on by default).

## [1.6.4] - 2026-06-14

### Fixed

- Login biometric and trust-device checkboxes hard to see when checked (dark checkmark on dark blue); now use `onPrimary` checkmark and clearer unchecked border.

## [1.6.3] - 2026-06-14

### Fixed

- Login Sign In / Verify button text unreadable (dark label on dark blue); button now uses theme `onPrimary` content color.

## [1.6.2] - 2026-06-14

### Fixed

- Hyundai Canada email OTP verification failing with HTTP 200 / “Failure”: validate and token steps now use the login email for `userAccount` (matching the official API) while send-OTP keeps the API account email for delivery.
- Hyundai Canada SMS OTP not sent when the API returns a partially masked phone (e.g. last four digits only); that masked value is now forwarded to `sendotp` instead of being dropped.
- Canada MFA success detection when `responseCode` is returned as a string, and when `verifiedOtp` is omitted but a validation key is present.

## [1.6.1] - 2026-06-14

### Fixed

- Canada Bluelink/Kia Connect SMS verification codes not delivered when the API returns a masked phone number (for example `XXXX`); the send-OTP request now omits masked numbers and lets the server resolve the account phone.
- Canada MFA now uses the API `userAccount` email from verification setup when `emailList` is absent, and uses that account email consistently in send/validate/token requests.

## [1.6.0] - 2026-06-14

### Added

- Full Canada MFA login for Hyundai, Kia, and Genesis (`7110` device verification flow with email/SMS OTP, trust-device for 90 days).
- Unified verification-code UI on the login screen for US Kia and Canada (method picker, resend with cooldown, trust-device checkbox).
- Background session refresh recovery when verification is required again (routes back to login with OTP entry).

### Changed

- US Kia OTP flow refactored onto shared verification challenge model with SMS/email delivery selection and resend support.

### Fixed

- Login screen OTP notice shown as a cut-off error at the bottom; verification message now appears in an info banner above the code field.
- Sign In / Verify button pinned above the keyboard so it stays visible while typing.
- Verification code field clipped at the top when the keyboard was open (scroll viewport inset + over-scroll).
- Hilt build failure with Kotlin 2.2 (`kotlinx-metadata-jvm` 2.2.0 unsupported on Hilt 2.52) by upgrading Dagger/Hilt to 2.59.2.
- Hilt `@AndroidEntryPoint` kapt failure on AGP 9 by migrating Hilt to KSP and using explicit entry point on `CarAppService`.
- Restored complete Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) so Android Studio can sync and enable the Run configuration.

## [1.5.0] - 2026-06-14

### Changed

- Rebranded from **BlueAndroid** to **BlueDeck** — app name, package (`com.bluedeck`), Gradle project name, Material 3 theme, widgets, Android Auto services, and documentation (see [NOTICE](NOTICE)).

## [1.4.0] - 2026-06-02

### Added

- Per-vehicle feature capability detection: climate seats, heated steering, EV charging, digital key, location, and valet controls are shown only when the vehicle API reports support (or an unknown state worth surfacing).
- Unit conversion helpers for temperature and distance display across screens and widgets.

### Changed

- Rebranded from **BlueBridge for Android** to **BlueAndroid** — app name, package (`com.blueandroid`), launcher and splash icons, Material 3 theme, widgets, Android Auto services, and in-app attribution (see [NOTICE](NOTICE)).
- Simplified settings: removed per-user theme customization in favor of the new default BlueAndroid look; streamlined About section with fork attribution.
- Dashboard, remote start, seat climate presets, and status screens adapt layout and controls to detected vehicle capabilities.

### Fixed

- Canada sign-in and session recovery after app resume; PIN-protected commands retry when the session expires.
- Region-specific API routing consolidated for Canada, EU, Australia/NZ, and US Kia paths after the redesign.
- Home screen widget provider metadata and layouts (launcher compatibility, sizing).
- Climate command handling for empty successful HTTP responses from some Bluelink endpoints.

## [1.3.10] - 2026-05-18

Initial public release of this fork, still distributed as **BlueBridge for Android** (`com.bluebridge.android`). Unofficial client for Hyundai Bluelink and Kia Connect; not affiliated with Hyundai, Kia, or the upstream BlueBridge maintainer beyond the open-source lineage in [LICENSE](LICENSE).

### Added

- Remote lock/unlock, engine start/stop with climate pre-configuration, horn and lights, and vehicle status (doors, tires, odometer, ignition).
- EV battery status, charge start/stop, and charge targets where supported.
- Material 3 UI with light, dark, and system themes; optional biometric app lock.
- Multi-region support: USA Hyundai/Kia, Canada (Hyundai, Kia, Genesis), experimental EU (refresh-token login), and Australia/NZ.
- Login-screen region and brand selection; imperial/metric and °F/°C preferences; time zone and 12/24-hour formatting for vehicle timestamps.
- Home screen widgets (full, battery, lock, unlock, climate, refresh, compact controls) backed by cached vehicle data.
- Walk-away lock via Bluetooth device disconnect detection.
- Dashboard vehicle images (model-based plus optional custom image).
- Android Auto read-only vehicle status companion.
- Secure local credential and token storage via DataStore.

### Changed

- Evolved from earlier `v1.0` baseline with expanded regional APIs, widget styling, and settings/status UI improvements.

[1.5.0]: https://github.com/tracer99/BlueDeck/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/tracer99/BlueDeck/compare/v1.3.10...v1.4.0
[1.3.10]: https://github.com/tracer99/BlueDeck/releases/tag/v1.3.10
