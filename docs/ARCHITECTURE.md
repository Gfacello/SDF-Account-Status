# Architecture

NetSuite SDF Account Status is a WebStorm plugin with an IntelliJ Platform UI boundary and Kotlin domain logic. It keeps raw CLI output out of logs and performs project reads and account discovery off the Event Dispatch Thread (EDT). This document describes the integrated 0.3.0 candidate; live provider/setup compatibility and recorded manual verification remain release gates.

## Runtime flow

```text
project.json ──> parsed snapshot ──> account workflow state ──> status label
                                          ▲                      │
                                          │                      │ click
configured provider ──> account metadata ──┘                      ▼
project document <── document gateway <── selection controller <── picker
```

1. `SdfAccountStatusBarWidgetFactory` creates one widget for each project with a base path.
2. `SdfAccountStatusResolver` reads and parses root-level `project.json` once into a `ProjectJsonSnapshot`, including file stamps around the read. A changed stamp causes a fresh read rather than publication of that snapshot.
3. `AccountStateController` schedules project reads and account loads independently. On the EDT it rejects superseded/disposed results before publishing a new `AccountWorkflowState` containing both the project snapshot and account-list state.
4. `ConfiguredAccountProvider` captures the current provider configuration and invokes exactly one provider. Node is the default; Java requires explicit selection. Failure never triggers another provider.
5. The Node provider runs public list/info commands in an empty temporary directory and validates all responses before returning accounts. The legacy loader parses Java CLI list output. Both return safe available/unavailable results.
6. `SdfAccountStatusPresentation` combines the current project identity, authoritative account metadata and saved display style. The same formatter supplies normal refresh and successful-selection presentation, full tooltips and accessible descriptions.
7. Clicking or keyboard-activating the label opens `AccountPickerPanel`. Its model builds current/recommended sections, account-family/company groups, search and combined environment/role filters.
8. The picker renders account rows, counts, loading/error states and fixed footer actions. Refresh retains valid filters; a vanished role resets only the role filter with an explanation. Activation uses `AccountWorkflowController`; production requires confirmation, and cancellation restores all three filters.
9. `IntellijProjectJsonGateway` updates the live IDE document in a write command, saves it and verifies the requested default before reporting success. The state controller then publishes the saved identity and schedules a fresh project snapshot.
10. Browser actions, display/provider settings and Oracle setup have separate controllers. Browser opening and setup have no project-document writer; setting the project default remains an explicit picker selection.

## Main components

| Component | Responsibility |
| --- | --- |
| `SdfAccountStatusBarWidgetFactory` / widget | Widget lifecycle, component wiring, popup/context menus, VFS and polling triggers |
| `AccountStateController` / `AccountWorkflowScheduler` | Background work, cancellation, generation checks and coherent EDT publication |
| `AccountWorkflowController` | Current-account checks, production confirmation and selection policy |
| `AccountPickerSelectionUi` | Picker effects and full filter restoration after cancelled confirmation |
| `IntellijProjectJsonGateway` | Live-document write/save transaction and persistence verification |
| `AccountPickerPanel` / `AccountPickerModelBuilder` | Search, environment/role filters, grouping/ranking, counts, refresh and setup controls |
| `SdfAccountStatusResolver` / `ProjectJsonSnapshot` | One strict parse per project snapshot and change-during-read detection |
| `SdfAccountStatusPresentation` | Shared status text, environment treatment, tooltips and accessibility |
| `SdfProjectJsonParser` / `SdfProjectJsonUpdater` | Strict JSON interpretation and token-local authentication-field edits |
| `ConfiguredAccountProvider` / `AccountProviderConfiguration` | Explicit provider selection, path validation and immutable configuration capture |
| `AccountProviderSettingsController` / dialog | Validated configuration edits and cancellation/disposal handling |
| `NodeSuiteCloudCommandResolver` | Node executable and JavaScript entrypoint discovery or explicit paths |
| `NodeSuiteCloudProcessExecutor` | Bounded output/time, cancellation and observed child-process termination |
| `NodeSuiteCloudAccountProvider` / `NodeSuiteCloudOutputParser` | Public list/info sequence, structured metadata and complete-result validation |
| `InstalledSuiteCloudCliExecutor` / `SuiteCloudAuthListLoader` / `SdfAuthListParser` | Separate legacy Java list provider |
| `AccountSetupController` / `SuiteCloudAccountSetup` | Open Oracle settings, then reload and reopen the chooser |
| `AccountBrowserController` / `NetSuiteAccountUrl` | Validated saved account URLs and browser opening without default switching |
| `SdfAccountPreferences` / `AccountPreferencesListener` | Local persisted settings and updates across open projects |
| `SdfAccountEnvironmentClassifier` / `SdfAccountRecommender` | Authoritative environment classification and deterministic related-account ranking |

Interfaces separate selection, scheduling, persistence, browser and setup effects from policy. Pure tests cover those boundaries; IntelliJ fixtures cover document and Swing behavior.

## Threading and lifecycle

- UI mutations and state publication run on the EDT. Project-file reads, CLI discovery, subprocess execution and parsing run on the application executor.
- Account and project loads have separate generations. Cancelling or superseding a worker is not enough by itself: the delivery callback also checks its generation and widget/project disposal before changing state.
- Node commands have a 30-second per-process and 120-second whole-refresh budget. Detail lookups run at most two at a time, retain the listed order, and report completed/total progress on the UI thread. Each command receives the remaining refresh budget. Output is bounded at 1 MiB per stream and decoded strictly as UTF-8; stdin is closed. Cleanup terminates the launcher and observed descendants and closes/cancels readers.
- Widget disposal cancels background work and polling, disposes project/application message-bus connections, and closes the popup on the UI thread.
- Closing a picker alone retains a useful in-flight account load. Reopening reads current workflow state.
- Account records are retained in memory. Raw CLI output is not persisted by the plugin.

VFS events trigger prompt project refresh. A two-second timestamp poll covers external changes that do not generate the expected event. The immutable project snapshot supplies both the current identity and presentation; the widget does not independently reparse the same file for each purpose.

## `project.json` behavior

The project file must contain a JSON object. Comments, trailing commas, single-quoted strings, extra top-level values and malformed content are rejected.

Update rules:

- If only `defaultAuthId` exists, update it.
- If only legacy `DefaultAuthID` exists, update it without adding another key.
- If both exist, update both.
- If neither exists, add `defaultAuthId`.
- Preserve unrelated JSON values, formatting and the source newline convention.

The pure updater returns transformed content. The gateway applies it to the live editor document, preserving unrelated unsaved edits, and saves through the IDE. Success requires a saved document whose parsed default matches the requested authentication ID. If save fails, rollback restores the original text only when the document still equals the plugin's attempted result; newer changes made by a save listener are not overwritten. Failure leaves the user a fixed message to inspect `project.json`.

## Provider and setup boundaries

The Node provider uses only `suitecloud account:manageauth --list` and `--info <authentication-id>`, invoking the installed Node executable with a JavaScript entrypoint directly. It resolves absolute PATH entries and known npm locations, or uses validated explicit absolute paths. The settings UI requires the JavaScript entrypoint rather than a shell wrapper. No shell, `npm`, `npx`, installation command or private SDK API is invoked.

Every refresh runs in a fresh empty temporary directory because Oracle loads `suitecloud.config.js` and command hooks from the working directory. The CLI inherits the IDE user/authentication environment; the plugin does not inspect credential stores or copy authentication environment values into diagnostics. List/info results must match completely: unknown formats or failed detail lookups make the entire refresh unavailable. Role/company metadata comes from separately labelled fields, so punctuation in one field does not become a delimiter for another.

The legacy provider searches only the `.suitecloud-sdk` root, preferring an SDK-contained `sdfcli` and retaining its CLI JAR as a fallback. This legacy launcher/JAR fallback is separate from provider selection: a failed Node request never switches to Java. See the [recorded Oracle sources, versions and live gates](NODE-CLI-COMPATIBILITY.md).

**Add an account** closes the picker before opening Oracle's Account Management through public `ShowSettingsUtil`. It checks that the Oracle plugin is loaded, selects its settings page by configurable ID, and provides manual-navigation guidance if unavailable. When settings were shown, the controller reloads accounts and reopens with cleared filters, including after cancellation because Oracle can save an identity before closing. If settings could not be opened, it restores the original filters. It has no project writer and invokes no `account:setup` command. Oracle settings-page stability, actual default preservation and shared identity visibility still need live verification.

## Presentation, preferences and browser actions

The default label remains the authentication ID. Optional account details show abbreviated company · environment · role, with full metadata in an escaped HTML tooltip and plain-text accessible description. Missing metadata falls back to the authentication ID and Unknown environment.

Environment classification uses actual CLI account IDs:

- `_SB` with optional digits is sandbox.
- Digits-only IDs are production.
- `_RP` with optional digits is Release Preview.
- Development, test-drive and other nonstandard IDs remain unknown.

Authentication aliases are never used as authoritative environment evidence. Recommendations favor the same normalized family, a complementary environment, a distinct account ID, the same company and then the same role, with deterministic ordering and a bounded visible count.

`SdfAccountPreferences` is an application service persisted in `sdfAccountStatus.xml` with `RoamingType.DISABLED`. It stores display style, selected provider, explicit runtime paths and URLs keyed by normalized authoritative account ID. These settings are not written into the SDF project or synchronized by Settings Sync. Provider and style changes are published to open widgets; provider changes reload account lists.

Status and account-row context menus support mouse and keyboard access. Browser targets are captured from the current or selected account's authoritative metadata, independently of the project-default selection path. The user supplies a Company URLs NetSuite UI base URL; validation permits only HTTPS account-specific `app.netsuite.com` hosts with no credentials, port, query, fragment or non-root path. The plugin never derives a domain from the account ID. Browser session/login controls the browser role, not the CLI identity.

## Security and privacy

The plugin never opens SDK credential files. Captured CLI output is not logged, included in exception messages or exposed by its result's `toString()`. CLI failures use fixed messages and provider labels; ordinary workflow code does not transmit account lists. Browser and Oracle tooling use their own normal network flows.

The plugin implements no analytics or automatic telemetry. It registers the IDE's built-in JetBrains Marketplace exception reporter; the IDE can submit reports manually or automatically according to its error-reporting settings. This platform-managed reporting is independent of the account-list workflow. See [JetBrains error-handler documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html) and [security reporting guidance](../SECURITY.md).

## Verification strategy

- Unit tests cover strict JSON behavior, environment/grouping rules, parsing, provider sequencing and safe failures, snapshot consistency, scheduling races, stale-result rejection and controller effects.
- IntelliJ fixture tests exercise live documents, save failure/listener behavior, real EDT delivery, picker keyboard/mouse actions and local preferences.
- Synthetic JDK subprocess tests exercise path/argument handling, stdout/stderr limits, timeout, malformed UTF-8, cancellation and child-process cleanup without real credentials or an installed CLI.
- The CI configuration retains Ubuntu's complete package/structure/compatibility gate and adds focused synthetic Node tests on macOS and Windows. A configured job is not evidence that a remote platform run passed; record its actual result separately.
- `verifyPluginProjectConfiguration` checks build configuration, `verifyPluginStructure` checks the descriptor/archive, and `verifyPlugin` targets WebStorm 2026.1 and 2026.2.
- The integrated candidate still requires the recorded full gate, ZIP inspection, manual IDE checks and live Node/Oracle/Add Account checks in [GOAL-0.3.0.md](GOAL-0.3.0.md). Branch-level test results do not establish those complete-candidate requirements.
