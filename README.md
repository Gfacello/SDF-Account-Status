# NetSuite SDF Account Status

NetSuite SDF Account Status keeps the active SuiteCloud Development Framework (SDF) authentication ID visible in the WebStorm status bar and lets you change it without leaving the IDE.

The project is independent and is not affiliated with or endorsed by Oracle or JetBrains.

## Features

- Reads `defaultAuthId` and the legacy `DefaultAuthID` key from a root-level `project.json`.
- Refreshes when `project.json` changes.
- Uses green for sandbox, red plus a critical icon for production, and yellow for unknown or unavailable state.
- Opens a searchable account picker from the status-bar label.
- Searches authentication ID, account name, account number, role, and environment.
- Places the current account and closely related production, sandbox, or Release Preview accounts first.
- Groups authentication IDs under one combined account-family/customer row in each collapsible section.
- Aligns authentication ID, account ID, environment, and role in a compact tree table.
- Uses a resizable chooser, remembers its dimensions, and keeps **Open project.json** fixed at bottom right.
- Requires confirmation before switching to a production account.
- Updates only the supported authentication-ID fields while preserving unrelated JSON values.

## Compatibility

| Component | Supported configuration |
| --- | --- |
| IDE | WebStorm 2026.1 or later (build 261+) |
| Plugin Verifier matrix | WebStorm 2026.1 and 2026.2 |
| Project | SuiteCloud project with a valid `project.json` at the project root |
| Account picker | Existing SuiteCloud CLI for Java installation in the SDK layout described below |

The plugin descriptor intentionally requires the WebStorm and JavaScript modules. Each future WebStorm release should pass Plugin Verifier before a plugin release is published for it.

## Install a local build

1. Build the distribution with `./gradlew clean test buildPlugin` or download a ZIP from the project releases.
2. In WebStorm, open **Settings | Plugins**.
3. Select the gear menu, choose **Install Plugin from Disk**, and select `build/distributions/netsuite-sdf-account-status-<version>.zip`.
4. Restart WebStorm if requested.

Do not extract the ZIP before installing it.

## SuiteCloud CLI dependency

The status-bar display reads `project.json` without SuiteCloud CLI. The account picker additionally requires an existing local SuiteCloud CLI for Java installation.

The adapter looks only inside the configured SDK root (`~/.suitecloud-sdk`): it uses an executable `sdfcli` found at the SDK root or in its `bin` or `cli` directory, otherwise it supports the Java CLI JAR in this layout:

```text
.suitecloud-sdk/
├── sdfcli                  # optional launcher location
├── bin/sdfcli              # optional launcher location
└── cli/
    ├── sdfcli              # optional launcher location
    └── cli-<version>.jar   # Java CLI fallback
```

It executes only the equivalent of this read-only operation:

```bash
sdfcli manageauth -list
```

The plugin does not search arbitrary `PATH` entries, bundle SuiteCloud CLI, authenticate accounts, decrypt credentials, or read credential-file contents. If the CLI is missing, fails, times out, returns unexpected output, or has no configured accounts, the chooser explains that the account list is unavailable and leaves `project.json` unchanged.

### SuiteCloud CLI for Java lifecycle

Oracle ended support for SuiteCloud CLI for Java at version 2025.2 and states that its downloads remain available only until NetSuite 2027.1. Existing installations can still supply the account list, but this plugin integration inherits that lifecycle limitation. SuiteCloud CLI for Node.js is not yet used by this version of the plugin.

- [Oracle: SuiteCloud CLI for Java commands and lifecycle](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1489072226.html)
- [Oracle: `manageauth -list`](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_157122305053.html)

## Use the account picker

1. Open a SuiteCloud project in WebStorm.
2. Click the SDF authentication ID in the status bar.
3. Wait for the local CLI account list to load.
4. Type any part of an authentication ID, company name, account number, role, or environment.
5. Double-click an account, or select it and press **Enter**. Production accounts require confirmation.

The plugin validates the current document as strict JSON, updates `defaultAuthId`, `DefaultAuthID`, or both when both already exist, saves the document, refreshes the widget immediately, and closes the chooser. Unrelated JSON values and the existing layout are preserved.

Use **Open project.json** at the bottom of the chooser when you want to inspect or edit the file directly.

## Environment detection

After the account list is loaded, environment badges use the actual account ID returned by SuiteCloud CLI:

- An account ID ending in `_SB` followed by digits is **Sandbox**.
- A digits-only account ID is **Production**.
- An account ID ending in `_RP` is **Release Preview**.
- Development, test-drive, and other nonstandard IDs are **Unknown**.

Before authoritative CLI metadata is available, the status remains yellow and treats the authentication ID as **Unknown**. Authentication IDs are user-defined, so the plugin never guesses that one is safe from its name.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `project.json not found` | Open the SuiteCloud project directory itself and confirm `project.json` is at its root. |
| `SuiteCloud CLI is unavailable` | Confirm `~/.suitecloud-sdk` contains an executable `sdfcli` at its root or in `bin` or `cli`, or a `cli/cli-<version>.jar` with a compatible Java runtime. |
| `No SuiteCloud authentication IDs are configured` | Run `sdfcli manageauth -list` in a terminal and configure an authentication ID with Oracle's supported tooling. |
| `Unrecognized account list` | Confirm the installed Java CLI version and run its list command manually. Raw CLI output is intentionally not copied into the IDE error. |
| Account remains yellow | Open the chooser and use **Retry** if the background CLI load failed. Confirm that the current authentication ID appears in `sdfcli manageauth -list`. |
| `project.json` is unchanged | Confirm it is valid JSON and writable. The plugin rejects comments, trailing commas, and other nonstandard JSON rather than rewriting them. |

Never paste credentials, tokens, private keys, or unsanitized CLI output into a public issue.

## Privacy and data handling

- All normal plugin processing is local.
- The plugin reads the project-root `project.json` and discovers only an SDK-contained executable `sdfcli` launcher or the installed SDK CLI JAR by filename.
- It starts only the account-list operation and captures its account/authentication/role display output in process memory.
- Raw CLI standard output and standard error are never written to the IDE log or shown in error messages.
- The plugin has no automatic analytics or telemetry and does not transmit account-list data.
- Selecting an account writes only the selected authentication ID to the supported `project.json` field or fields.
- Fatal error reports use the built-in JetBrains Marketplace Exception Analyzer. Reports may be submitted manually or automatically according to the IDE's error-reporting settings. Reports may include stack traces, IDE/OS/JVM/plugin metadata, user comments, and user-selected attachments. This IDE-managed reporting is separate from the plugin's analytics behavior. See [JetBrains error-handler documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html).

See [SECURITY.md](SECURITY.md) for responsible disclosure guidance.

## Development

Prerequisites:

- JDK 21
- Network access for the first Gradle/IDE dependency resolution

Common commands:

```bash
./gradlew clean test
./gradlew runIde
./gradlew buildPlugin
./gradlew verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
```

The plugin ZIP is created at:

```text
build/distributions/netsuite-sdf-account-status-<version>.zip
```

Additional documentation:

- [Contributing](CONTRIBUTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Release process](docs/RELEASING.md)
- [Changelog](CHANGELOG.md)

## License

Released under the [MIT License](LICENSE).
