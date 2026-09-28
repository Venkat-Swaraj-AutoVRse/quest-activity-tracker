# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] - 2026-09-28

### Added
- Continuous Integration workflow that builds the debug APK and publishes it to
  GitHub Releases (manual trigger via the Actions tab).
- Community-health files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  issue templates, and a pull request template.
- Custom launcher icon.

### Changed
- Unified the source package to `com.autovrse.quest.activitytracker`.
- App now opens as a landscape 2D panel (1024x640dp default) on Horizon OS.

### Fixed
- Compose Compiler / Kotlin version mismatch that broke the build.
- Launcher activity `ClassNotFoundException` on launch.

## [1.0.0] - 2026-09-28

### Added
- Screen-time dashboard with Today / Week / Month ranges.
- Time-distribution donut chart and app leaderboard.
- Per-app usage limits persisted via `SharedPreferences`.
- Web companion server (Ktor) serving a browser dashboard with JSON/CSV export.

[Unreleased]: https://github.com/Venkat-Swaraj-AutoVRse/quest-activity-tracker/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/Venkat-Swaraj-AutoVRse/quest-activity-tracker/compare/v1.0.0-5...v1.0.1
[1.0.0]: https://github.com/Venkat-Swaraj-AutoVRse/quest-activity-tracker/releases/tag/v1.0.0-5
