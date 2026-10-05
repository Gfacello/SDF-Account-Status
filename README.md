# NetSuite SDF Account Status

NetSuite SDF Account Status keeps the active SuiteCloud Development Framework (SDF) authentication ID visible in the WebStorm status bar and lets you change it without leaving the IDE.

The project is independent and is not affiliated with or endorsed by Oracle or JetBrains.

This branch contains the 0.3.0 release candidate. Live Node CLI/Oracle WebStorm authentication parity and **Add an account** checks remain release gates; implementation and synthetic tests do not establish that compatibility. See the [compatibility evidence and outstanding checks](docs/NODE-CLI-COMPATIBILITY.md).

## Features

- Reads `defaultAuthId` and the legacy `DefaultAuthID` key from a root-level `project.json`.
- Refreshes when `project.json` changes.
- Uses green for sandbox, red plus a critical icon for production, and yellow for unknown or unavailable state.
- Opens a searchable account picker from the status-bar label.
- Searches authentication ID, account name, account number, role, and environment.
- Combines search with environment and role filters, matching counts, and **Clear filters**.
- Refreshes the account list on demand while retaining valid filters and the project default.
- Places the current account and closely related production, sandbox, or Release Preview accounts first.
- Groups authentication IDs under one combined account-family/customer row in each collapsible section.
- Aligns authentication ID, account ID, environment, and role in a compact tree table.
- Uses a resizable chooser, remembers its dimensions, and keeps **Open project.json** fixed at bottom right.
- Requires confirmation before switching to a production account.
- Updates only the supported authentication-ID fields while preserving unrelated JSON values.
- Opens the current or a selected account in NetSuite using a locally saved account URL.
- Offers an optional company · environment · role status label with full details in its tooltip.
- Opens Oracle's Account Management settings from **Add an account**, then refreshes the chooser.

### Status display

Right-click the status label and use **Status display** to choose **Authentication ID** (the default) or **Account details**. The detailed label shows company, environment and role, abbreviating long names; the tooltip and screen-reader description retain full values. Until account metadata loads, either style shows the authentication ID with an unverified environment. The choice persists in local IDE settings and applies to all open projects.

### Open in NetSuite

Right-click the status label for the current account, or an account row for that row, and choose **Open in NetSuite**. The context menu also opens with Shift+F10 or the keyboard's context-menu key. This action does not change the SDF project default.

On first use, paste the **NetSuite UI** base URL from **Setup > Company > Company Information > Company URLs**. Use an HTTPS account-specific `app.netsuite.com` URL without credentials, a port, path, query, or fragment; a trailing slash is accepted. **Set account URL…** edits it later. URLs are stored only in the IDE's local settings, shared by roles for the same account, with separate production and sandbox mappings. The plugin never constructs a URL from an account ID. See [Oracle's account-specific domain guidance](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1498251763.html).

The browser uses its current session or normal sign-in flow; opening a URL does not select the CLI authentication role in the browser. Load account metadata before configuring a URL.

## Compatibility

| Component | Supported configuration |
| --- | --- |
| IDE | WebStorm 2026.1 or later (build 261+) |
| Plugin Verifier matrix | WebStorm 2026.1 and 2026.2 |
| Project | SuiteCloud project with a valid `project.json` at the project root |
| Account picker | Existing SuiteCloud CLI for Node.js with its required Node.js and Java runtimes; explicitly selected Java CLI is a legacy alternative |
| Add an account | Oracle's SuiteCloud IDE Plug-in for WebStorm installed and enabled; live compatibility checks pending |

The plugin descriptor intentionally requires the WebStorm and JavaScript modules. Each future WebStorm release should pass Plugin Verifier before a plugin release is published for it.

## Install a local build

1. Build the distribution with `./gradlew clean test buildPlugin` or download a ZIP from the project releases.
2. In WebStorm, open **Settings | Plugins**.
3. Select the gear menu, choose **Install Plugin from Disk**, and select `build/distributions/netsuite-sdf-account-status-<version>.zip`.
4. Restart WebStorm if requested.

Do not extract the ZIP before installing it.

## Account provider configuration

The authentication-ID status display reads `project.json` without SuiteCloud CLI. Account metadata, environment detection and the picker require an existing CLI installation. The candidate defaults to **Node.js CLI**.

Right-click the status label and choose **Account provider: … > Configure account provider…**. Select the provider, and optionally enter absolute paths to the Node.js executable and SuiteCloud JavaScript entrypoint (`suitecloud.js`). Leave either path blank for discovery from the IDE's inherited `PATH` and common npm installation locations. An invalid explicit path produces an error instead of silently choosing another installation. These preferences are local to the IDE, apply to all open projects, and are excluded from Settings Sync. Applying a provider change reloads account lists.

The Node provider invokes the installed Node executable directly with SuiteCloud's JavaScript entrypoint. It does not run shell/npm wrappers or install tooling. Its only account commands are:

```bash
suitecloud account:manageauth --list
suitecloud account:manageauth --info <authentication-id>
```

Each refresh runs in an empty temporary directory, keeping the project's `suitecloud.config.js` hooks out of account discovery. It inherits the IDE's user and authentication environment; a CLI launched from a different terminal environment may see different identities. Install the CLI and runtimes according to [Oracle's prerequisites](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1558708810.html). The researched versions and output contract are recorded in [Node CLI compatibility](docs/NODE-CLI-COMPATIBILITY.md).

### Legacy Java provider

Choose **Java CLI (legacy)** explicitly when using an existing Java installation. A Node failure never triggers automatic fallback. The Java adapter looks only inside `~/.suitecloud-sdk`: it uses an executable `sdfcli` found at the SDK root or in its `bin` or `cli` directory, otherwise it supports the Java CLI JAR in this layout:

```text
.suitecloud-sdk/
├── sdfcli                  # optional launcher location
├── bin/sdfcli              # optional launcher location
└── cli/
    ├── sdfcli              # optional launcher location
    └── cli-<version>.jar   # Java CLI fallback
```

This provider executes only the equivalent of this read-only operation:

```bash
sdfcli manageauth -list
```

Neither provider bundles SuiteCloud CLI, decrypts credentials, or reads credential-file contents. If the selected provider is missing, fails, times out, returns unexpected output, or has no configured accounts, the chooser identifies that provider in its error and leaves `project.json` unchanged. **Retry** and **Refresh** use the selected provider.

### SuiteCloud CLI for Java lifecycle

Oracle ended support for SuiteCloud CLI for Java at version 2025.2 and states that its downloads remain available only until NetSuite 2027.1. This is an end-of-support/download lifecycle, not a claim that installed CLIs stop running on that date. The candidate includes the Node provider to move toward supported tooling; its live cross-tool compatibility remains to be demonstrated.

- [Oracle: SuiteCloud CLI for Java commands and lifecycle](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1489072226.html)
- [Oracle: `manageauth -list`](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_157122305053.html)
- [Oracle: Node `account:manageauth`](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_157304934116.html)

## Use the account picker

1. Open a SuiteCloud project in WebStorm.
2. Click the SDF authentication ID in the status bar.
3. Wait for the local CLI account list to load.
4. Type any part of an authentication ID, company name, account number, role, or environment. Combine this with the environment and role selectors when useful.
5. Double-click an account, or select it and press **Enter**. Production accounts require confirmation.

The plugin validates the current document as strict JSON, updates `defaultAuthId`, `DefaultAuthID`, or both when both already exist, saves the document, refreshes the widget immediately, and closes the chooser. Unrelated JSON values and the existing layout are preserved.

Use **Open project.json** at the bottom of the chooser when you want to inspect or edit the file directly.

**Refresh** reloads accounts without changing the project default. It retains the search and environment filter, and retains the selected role if that role still exists. If a role disappears, the picker resets only that filter and explains why. **Clear filters** resets all three controls. Cancelling a production-switch confirmation restores the search, environment and role filters. Refresh and account activation are disabled while the list is loading or a selection is being saved.

### Add an account

Choose **Add an account** in the picker to open Oracle's **Settings | Tools | NetSuite | Account Management** page. Oracle's plugin owns sign-in and credential storage; this plugin opens the page through the public IntelliJ settings API. If the Oracle plugin or its settings page is unavailable, the picker is restored and guidance is shown.

After that settings dialog closes, the candidate reloads accounts and reopens the chooser with cleared filters so a newly added identity is visible. It also refreshes after cancellation because Oracle may have saved an identity before the dialog closed. Adding an identity does not ask this plugin to change `project.json`; selecting the new project default remains a separate action. This separation and visibility across Oracle/Node authentication contexts still require the [live Add Account checks](docs/NODE-CLI-COMPATIBILITY.md#validation-evidence-and-remaining-gates).

## Environment detection

After the account list is loaded, environment badges use the actual account ID returned by SuiteCloud CLI:

- An account ID ending in `_SB` with optional digits is **Sandbox**.
- A digits-only account ID is **Production**.
- An account ID ending in `_RP` with optional digits is **Release Preview**.
- Development, test-drive, and other nonstandard IDs are **Unknown**.

Before authoritative CLI metadata is available, the status remains yellow and treats the authentication ID as **Unknown**. Authentication IDs are user-defined, so the plugin never guesses that one is safe from its name.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `project.json not found` | Open the SuiteCloud project directory itself and confirm `project.json` is at its root. |
| Provider is unavailable | Check the provider named in the message. For Node, confirm its Node/Java prerequisites and configure absolute executable/entrypoint paths if the IDE cannot discover them. For legacy Java, check the SDK layout above. |
| No authentication IDs are configured | Verify the selected CLI under the same OS user and authentication context as the IDE. Configure an identity with Oracle's supported tools, then **Refresh**. |
| Unrecognized account details/list | Compare the selected CLI version with the recorded compatibility evidence. Raw CLI output is intentionally excluded from the IDE error. |
| Account remains yellow | Use **Refresh** or **Retry** and confirm that the current authentication ID is visible to the selected provider. Nonstandard account IDs remain Unknown. |
| Added account is missing | Clear filters, refresh, and compare Oracle's Account Management context with the selected provider. Live cross-tool parity is a release gate, not an assumed storage guarantee. |
| `project.json` is unchanged | Confirm it is valid JSON and writable. The plugin rejects comments, trailing commas, and other nonstandard JSON rather than rewriting them. |

Never paste credentials, tokens, private keys, or unsanitized CLI output into a public issue.

## Privacy and data handling

- Project parsing, filtering and preferences are local. Browser opening and Oracle's sign-in/CLI behavior use their own normal network flows.
- The plugin reads the project-root `project.json` and discovers the selected provider's installed executable/entrypoint.
- It starts only read-only account-list/detail commands and captures account/authentication/role display output in process memory. Oracle's tooling manages its own credentials; this plugin does not read those files.
- Display style, provider selection, explicit runtime paths and account URL mappings are stored in local IDE preferences, not in the project or Settings Sync.
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
