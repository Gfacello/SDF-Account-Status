# Architecture

NetSuite SDF Account Status is a small WebStorm plugin with an IntelliJ Platform UI boundary and pure Kotlin core logic. The design keeps credential-adjacent process output out of logs and keeps filesystem/process work away from the Event Dispatch Thread (EDT).

## Runtime flow

```text
project.json ──> status resolver ──> status-bar label
     ▲                                  │
     │                                  │ click
     │                                  ▼
selected auth ID <── chooser <── parsed `manageauth -list` output
```

1. `SdfAccountStatusBarWidgetFactory` creates one widget for each project with a base path.
2. `SdfAccountStatusResolver` reads the root-level `project.json`, extracts the authentication ID, and creates the label state.
3. VFS notifications and a low-frequency timestamp poll refresh the label when the file changes.
4. `SuiteCloudAuthListLoader` preloads the local read-only account list on an application executor thread; the popup shows a loading state if that work is still running.
5. Clicking or keyboard-activating the label opens the account chooser.
6. `SdfAuthListParser` accepts only known complete output formats. Any malformed row makes the entire account list unavailable.
7. The picker model marks the current account, adds related recommendations, and groups searchable metadata into one account-family/customer tier.
8. The tree-table popup renders each combined group directly below its collapsible section, followed by aligned account/environment/role rows with loading, empty, error, and retry states.
9. Activating an account validates and transforms the JSON, applies the result in an IntelliJ write command, saves it, refreshes the widget, and closes the popup. Production activation first requires confirmation.

## Main components

| Component | Responsibility |
| --- | --- |
| `SdfAccountStatusBarWidgetFactory` | Widget lifecycle and availability |
| `SdfAccountStatusBarWidget` | Status UI, popup lifecycle, background dispatch, document save, and refresh |
| `AccountPickerPanel` | Compact searchable tree-table UI, keyboard interaction, fixed footer action, and loading/error states |
| `AccountPickerModelBuilder` | Current/recommended sections, combined account-family/customer grouping, deterministic sorting, and filtering |
| `SdfAccountStatusResolver` | Converts project/file/authentication state into presentation-neutral widget state |
| `SdfProjectJsonParser` | Reads canonical and legacy authentication-ID fields from strict JSON |
| `SdfProjectJsonUpdater` | Replaces or inserts only supported top-level value tokens while preserving unrelated JSON text |
| `InstalledSuiteCloudCliExecutor` | Discovers the installed Java CLI and executes the account-list operation with a timeout |
| `SuiteCloudAuthListLoader` | Maps process and parse outcomes to safe available/unavailable results |
| `SdfAuthListParser` | Parses authentication ID plus account, company, and role display information |
| `SdfAccountEnvironmentClassifier` | Classifies authoritative CLI account IDs and normalizes account-family environments |
| `SdfAccountRecommender` | Ranks same-family and same-company accounts with deterministic limits |

Core result and model types avoid IntelliJ dependencies where practical so their behavior can be tested without launching an IDE.

## Threading and lifecycle

- Status-bar and popup component mutations run on the EDT.
- CLI discovery, process execution, and output parsing run on `AppExecutorUtil.getAppExecutorService()`.
- CLI execution has a bounded timeout and the future is cancelled when the widget is disposed or a manual retry supersedes it.
- The project message-bus connection and scheduled polling task are owned by the widget disposable.
- A popup instance is reused only while visible; closing it clears popup references without discarding a useful in-flight account load.
- Parsed authentication/account/role display records and the authentication-ID-to-environment map are retained in memory. Raw CLI output is not persisted by the plugin.

The VFS listener provides prompt refresh for IDE-visible changes. Timestamp polling covers external changes that do not produce the expected event.

## `project.json` behavior

The project file must contain a JSON object. Parsing is strict: comments, trailing commas, single-quoted strings, extra top-level values, and malformed content are rejected.

Update rules:

- If only `defaultAuthId` exists, update it.
- If only legacy `DefaultAuthID` exists, update it without adding another key.
- If both exist, update both to keep them consistent.
- If neither exists, add `defaultAuthId`.
- Preserve unrelated JSON values and the source newline convention.

The updater returns transformed content rather than writing a file itself. The widget owns the IntelliJ document write/save transaction and attempts to restore the original document text if saving fails.

## Environment and recommendation rules

CLI account IDs are the preferred environment source:

- IDs ending in `_SB` plus digits are sandbox.
- Digits-only IDs are production.
- IDs ending in `_RP` are Release Preview.
- Development, test-drive, and nonstandard IDs remain unknown.

Before the CLI map is available, the status resolver uses Unknown rather than inferring safety from the user-defined authentication ID.

Recommendations favor the same normalized account family, a complementary environment, a distinct account ID, the same normalized company, and then the same role. Ordering falls back to the authentication ID for deterministic results, and the visible recommendation count is bounded.

## Security and privacy

The CLI boundary accepts only display output from `manageauth -list`. The plugin never opens SDK credential files. Captured standard output and standard error are not logged, included in exception messages, or exposed by `toString()`. User-facing failures are fixed, non-sensitive messages.

The plugin does not implement analytics or automatic telemetry. It registers the IDE's built-in JetBrains Marketplace exception reporter; the IDE can submit reports manually or automatically according to its error-reporting settings. This platform-managed reporting is independent of the plugin's account-list workflow. See [JetBrains error-handler documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html).

See [../SECURITY.md](../SECURITY.md) for disclosure instructions.

## External dependencies and limitations

- IntelliJ Platform/WebStorm supplies the UI, VFS, command, document, and process APIs.
- Gson is supplied by the target platform and is used for strict JSON validation and safe string encoding.
- SuiteCloud CLI is a separate local runtime dependency used only by the account picker.
- The current CLI adapter searches only the configured `.suitecloud-sdk` root, preferring an SDK-contained `sdfcli` launcher and retaining its `cli` JAR as a fallback. It never resolves `sdfcli` from the IDE process `PATH`. That Java CLI is end-of-support; a future adapter should support the current SuiteCloud CLI for Node.js without weakening output validation or redaction.
- Authentication IDs are user-defined and are never used as authoritative environment evidence.

## Verification strategy

- Unit tests cover strict JSON parsing/updating, environment classification, CLI resolution/parsing and safe failures, recommendation ordering, model grouping/search data, and fixed popup action placement.
- `verifyPluginProjectConfiguration` checks Gradle/IntelliJ project configuration.
- `verifyPluginStructure` validates the assembled descriptor and archive layout.
- `verifyPlugin` checks binary compatibility against WebStorm 2026.1 and 2026.2.
- Continuous integration performs those checks and publishes the ZIP and reports as workflow artifacts.
