# Node.js CLI account provider: 0.3.0 compatibility evidence

Status: provider implementation and synthetic validation; live cross-tool authentication and Add Account validation remain release gates.

## Provider decision

Use Oracle's public `suitecloud account:manageauth --list` and `suitecloud account:manageauth --info <authentication-id>` commands. The Node CLI remains supported after the Java CLI's end of support. Its current implementation still requires a JDK and invokes its SDK JAR internally. This plugin invokes the installed Node CLI entrypoint, not the internal JAR integration mode, private SDK modules, or credential storage.

The provider's `load()` returns the existing `SdfAuthListLoadResult`. The workflow can adapt it as `AccountProvider { nodeProvider.load() }`; cancelling its worker with `Future.cancel(true)` interrupts the process loop. Existing Java-provider classes remain separate; this provider performs no automatic fallback after a Node failure. Wiring and any transitional provider-selection policy belong to the release integration decision.

## Inspected versions and provenance

Research performed on 2026-09-21:

| Evidence | Version | Scope |
| --- | --- | --- |
| Locally installed Oracle package metadata and formatter | 3.1.2; NetSuite 2025.2; `cli-2025.2.1.jar` | Metadata inspected; pure formatter exercised with invented data only |
| Oracle master / tag commit `03d349ecbed3dd3f1f0557e964268d5d21663e68` | 4.0.0; NetSuite 2026.2 | Public formatter, messages, execution and command source inspected |
| Oracle tag commit `9a1bddb53b7f7bb88ae359bd645488c1400d85de` | 4.1.0; NetSuite 2026.2; `cli-2026.2.0.jar` | Public package, formatter, output-handler and command metadata inspected |

The 4.1.0 list/info format matches the inspected 4.0.0 format. Current Oracle documentation requires Node.js 24 LTS and Oracle JDK 17 or 21. The latest npm publication was not independently confirmed: the registry request failed certificate verification. No CLI installation or upgrade was performed during research, and no real account-list/info command or credential-store read was performed.

Sources:

- [Java CLI lifecycle](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1489072226.html)
- [Node CLI prerequisites](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1558708810.html)
- [Public account command documentation](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_157304934116.html)
- [4.1.0 package metadata](https://github.com/oracle/netsuite-suitecloud-sdk/blob/9a1bddb53b7f7bb88ae359bd645488c1400d85de/packages/node-cli/package.json)
- [4.1.0 formatter](https://github.com/oracle/netsuite-suitecloud-sdk/blob/9a1bddb53b7f7bb88ae359bd645488c1400d85de/packages/node-cli/src/utils/AccountCredentialsFormatter.js)
- [4.1.0 output handler](https://github.com/oracle/netsuite-suitecloud-sdk/blob/9a1bddb53b7f7bb88ae359bd645488c1400d85de/packages/node-cli/src/commands/account/manageauth/ManageAccountOutputHandler.js)
- [4.0.0 messages](https://github.com/oracle/netsuite-suitecloud-sdk/blob/03d349ecbed3dd3f1f0557e964268d5d21663e68/packages/node-cli/messages.json)
- [SDK-core package marked private](https://github.com/oracle/netsuite-suitecloud-sdk/blob/03d349ecbed3dd3f1f0557e964268d5d21663e68/packages/sdk-core/package.json)

## Public display-output contract

The installed 3.1.2 pure formatter produced this list row from invented metadata:

```text
example_sb | Developer @ Example Co | 1234567-sb1.app.netsuite.com
```

And this info block:

```text
Authentication ID: example_sb
Account Name: Example Co
Account ID: 1234567_SB1
Role: Developer
Domain: 1234567-sb1.app.netsuite.com
Account Type: Sandbox
```

Only list-row authentication IDs are used. Each identity is then matched against its own info response, which provides account ID, company and role without interpreting ambiguous role/company delimiters. The output contains a role **name**, despite command documentation describing a role ID. The provider preserves same-account/different-role identities.

Version 3.1.2's empty formatter emits no rows; 4.0.0/4.1.0 emit `There are no authentication IDs available.` Both are recognized. Exact English progress lines, terminal ANSI sequences and CRLF are handled. Other output, duplicate IDs/fields, missing fields, mismatched identities, unknown account types and error text fail the whole refresh; no partial list is published.

The shared model accepts structured account details alongside the formatted display string, preserving bracket-containing roles and company punctuation from the separately labelled fields. Legacy Java rows still use their existing parser. Authentication IDs beginning with a hyphen or outside ASCII letters/digits/underscore/hyphen are rejected rather than passed as CLI control options. This is a conservative compatibility limitation, not evidence that Oracle rejects every such preexisting identity.

Account classification and family grouping recognize Oracle's complete `_SB` and `_RP` suffixes with optional digits, including `123456_SB` and `123456_RP2`. These are classified from the actual account ID, never the authentication alias. Unrecognized nonnumeric account IDs retain the existing Unknown classification instead of inheriting the formatter's catch-all Production label.

The domain is not treated as a canonical NetSuite UI URL. Open in NetSuite retains the separately saved account URL approach.

## Launcher, execution context and resource limits

- Resolve a Node executable plus JavaScript launcher. Explicit absolute paths override discovery; invalid explicit paths fail without choosing another installation.
- Discover absolute PATH entries and common npm locations. Resolve Unix npm symlinks and the known JavaScript file beside Windows npm wrappers; never execute `.cmd`, `.bat`, a shell, `npm`, `npx`, or an install command.
- Start every refresh in a newly created, empty temporary directory. Do not use the SDF project directory. Oracle loads `suitecloud.config.js` and command hooks even for account management, so project cwd would run project code during refresh. Account management needs no SDF project. [Oracle executor](https://github.com/oracle/netsuite-suitecloud-sdk/blob/03d349ecbed3dd3f1f0557e964268d5d21663e68/packages/node-cli/src/core/CommandActionExecutor.js), [configuration loading and validation](https://github.com/oracle/netsuite-suitecloud-sdk/blob/03d349ecbed3dd3f1f0557e964268d5d21663e68/packages/node-cli/src/core/extensibility/CLIConfigurationService.js).
- Inherit the IDE process's user and authentication environment. Only terminal-color controls are changed. No passkey environment values are read into diagnostics, and no SDK credential files are read by the plugin.
- Close stdin; collect stdout/stderr in memory with a 1 MiB limit per stream, strict UTF-8 decoding and no raw-output logging. Errors use fixed redacted messages, including errors reported with exit code zero.
- Default budgets are 30 seconds per command and 120 seconds for the complete refresh. Each command receives the remaining whole-refresh budget. Timeout and cancellation terminate the launcher and observed descendants, including the SDK Java child; descendant tracking is refreshed while waiting. Output reader threads are daemon threads and are closed/cancelled during cleanup.
- Remove the empty temporary directory. If unexpected tooling writes files into it, leave those files rather than recursively deleting unknown content. The CLI may independently update its own Java-validation settings; the plugin does not change those settings directly.

## Validation evidence and remaining gates

Automated tests exercise the pure parser, provider command sequence/result handling, launcher discovery and actual subprocess I/O. The subprocess fixture uses the test JDK's source launcher so CI needs no Node installation or real accounts; it emits synthetic Oracle-format responses. Tests cover spaced paths, argument boundaries, working directory, preservation of project defaults, nonzero/stderr errors, output caps, timeout, cancellation and child-process termination. These tests verify this plugin's execution and parsing behavior; they do not prove a particular Oracle CLI installation, secure-storage service or WebStorm credential context works live.

On 2026-09-21, `./gradlew test --offline -Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=5000` passed on macOS with JDK 21: 109 tests, including 28 Node-provider tests, zero failures or errors. Real subprocess tests cover malformed UTF-8, a child running at timeout, explicit cancellation and thread interruption. Metadata regressions cover Oracle's SB/RP suffix variants through picker classification and account-family recommendations. Windows launcher discovery is tested with a synthetic npm directory layout on macOS; Windows process execution has not been demonstrated.

Before the provider/setup scope is marked demonstrated:

- [ ] Run the installed supported Oracle CLI version with a dedicated test account and record sanitized list/info evidence plus Node/JDK/CLI versions.
- [ ] Compare the test identity across Node, the existing Java provider and Oracle WebStorm Account Management under the same OS user, authentication mode and secure-storage environment.
- [ ] Add a dedicated test identity through the button; verify it appears on Refresh and the project's default and unrelated contents remain unchanged.
- [ ] Cancel account setup and verify no default change; verify missing-plugin and renamed/unavailable settings-page behavior.
- [ ] Exercise the actual Node+Java process tree on supported operating systems and real IDE launch environments.
- [ ] Obtain an explicit user scope decision if these gates cannot be demonstrated. Do not relabel fixture tests as live compatibility or silently omit Add Account/migration.

Oracle documents a shared `.suitecloud-sdk` user storage location, but browser-based and machine-to-machine contexts use separate credential files and cannot see each other's IDs. This supports the design but does not replace the live comparison. [Shared storage](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/article_1024042128.html), [execution contexts](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/article_0113125121.html).

`account:setup` is unsuitable as a direct add-only command in the user's project: it is interactive, changes the default after OAuth or selection, and writes a new `project.json` object containing `defaultAuthId`. The preserved Oracle WebStorm Account Management prototype remains the candidate setup flow until its gates pass. [Setup documentation](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/article_89132630266.html), [default-write implementation](https://github.com/oracle/netsuite-suitecloud-sdk/blob/03d349ecbed3dd3f1f0557e964268d5d21663e68/packages/node-cli/src/utils/AuthenticationUtils.js), [WebStorm adding an account](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/subsect_1530894035.html).
