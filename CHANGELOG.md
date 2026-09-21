# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.2.0] - 2026-09-07

### Added
- Searchable status-bar account picker populated through the read-only SuiteCloud CLI.
- Account and role detail display with the current authentication ID marked and selected.
- Safe `project.json` updates for both `defaultAuthId` and legacy `DefaultAuthID`.
- Focused tests for CLI parsing, unavailable states, and JSON updates.
- Related-account recommendations based on the current account family and company.
- Search by account name, number, role, or environment.
- Collapsible sections with a single combined account-family/customer grouping tier and aligned account, environment, and role columns.
- Production-account confirmation and explicit Release Preview classification.
- Opt-in exception reporting through the JetBrains Marketplace Exception Analyzer.
- Production-focused architecture, security, release, and contribution documentation.
- Continuous integration for tests, packaging, structure checks, and WebStorm compatibility verification.

### Changed
- Renamed the plugin to **NetSuite SDF Account Status** and the distribution to `netsuite-sdf-account-status`.
- Moved the existing **Open project.json** behavior into the account picker.
- Replaced the flat chooser with a compact, resizable tree table and a fixed bottom-right **Open project.json** action.
- Removed redundant per-row recommendation labels and flattened customer/account grouping into one row.
- Preloads authoritative account metadata off the UI thread and treats unavailable metadata as unverified.
- Restricted the plugin descriptor to WebStorm and its bundled JavaScript platform.
- Included the MIT license in the packaged plugin.
- Pinned the Gradle distribution checksum used by the wrapper.

### Fixed
- Prevented a Swing accessibility initialization crash when opening the account picker.

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
