# Changelog

All notable changes to this project will be documented in this file.

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
