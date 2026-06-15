# Changelog

All notable changes to BlueDeck are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

BlueDeck is a fork of [BlueBridge for Android](https://github.com/Nelwyn99) by Nelwyn99, maintained independently at [tracer99/BlueDeck](https://github.com/tracer99/BlueDeck).

## Versioning policy

- **Bug fixes**: patch bump (`1.4.0` → `1.4.1`) — update `versionName` in `app/build.gradle.kts`, increment `versionCode` by 1, and add a `CHANGELOG.md` entry.
- **Features**: minor bump (`1.4.0` → `1.5.0`) — same files.
- **Major releases** (`x.0.0`): only when explicitly requested.
- **Git**: each feature, plan, or fix is developed on a new branch (`feature/`, `fix/`, or `plan/`), not directly on `main`.

See `.cursor/rules/release-workflow.mdc` for agent workflow details.

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
