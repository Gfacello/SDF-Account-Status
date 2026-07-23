# Changelog

All notable changes to this project will be documented in this file.

## [0.1.1] - 2026-07-22

### Changed
- Added compatibility with WebStorm 2026.2 and future IDE builds by removing the upper build limit.
- Removed compiler-generated references to the deprecated status-bar `PlatformType` API.
- Added explicit Plugin Verifier coverage for WebStorm 2026.1 and 2026.2.
- Made the NetSuite and WebStorm focus explicit in the Marketplace description for better discoverability.

## [0.1.0] - 2026-04-24

### Added
- Status bar widget for current SDF default account from `project.json`.
- Environment highlighting:
  - Sandbox: green
  - Production: red + critical icon
  - Unknown: yellow
- Auto-refresh when `project.json` changes.
- Click action to open `project.json`.
- Refactored core logic modules (parser, classifier, resolver).
- Unit tests for parser, classifier, and resolver error states.
- CI workflow for `./gradlew test buildPlugin`.
