# Security policy

## Supported versions

Security fixes are made on the latest released minor version. Users should update to the newest available NetSuite SDF Account Status release before reporting a problem that may already be fixed.

## Report a vulnerability

Use a private [GitHub security advisory](https://github.com/Gfacello/SDF-Account-Status/security/advisories/new) to report a vulnerability. Do not open a public issue for a suspected security or privacy problem.

Include only the minimum information needed to reproduce the issue:

- Plugin and WebStorm versions
- Operating system
- A description of the impact and reproduction steps
- Synthetic `project.json` and CLI-output examples, when required

Do not include NetSuite credentials, OAuth tokens, private keys, real authentication IDs, customer/account names, account numbers, or raw SuiteCloud CLI output. Remove sensitive values from screenshots and stack traces before attaching them.

Use the public [issue tracker](https://github.com/Gfacello/SDF-Account-Status/issues) for ordinary bugs and feature requests that contain no sensitive information.

## Security boundaries

The plugin is designed around these constraints:

- It does not parse, decrypt, modify, or copy SuiteCloud credential-file contents.
- The default Node provider executes only `account:manageauth --list` and `account:manageauth --info <authentication-id>`. The explicitly selected legacy Java provider executes only `manageauth -list`; failures never switch providers automatically.
- Node discovery runs in an empty temporary directory to avoid executing project `suitecloud.config.js` hooks. It invokes Node plus the JavaScript entrypoint directly, closes stdin, and bounds time and output; cancellation terminates the launcher and observed descendants.
- CLI standard output and standard error remain in process memory and are not logged or included in user-facing failures.
- Malformed output, process failure, timeout, and an empty account list are treated as unavailable data.
- `project.json` is changed only after strict JSON validation and only through an IntelliJ write command.
- Selecting an account changes the supported authentication-ID field or fields; unrelated JSON values are retained.
- **Add an account** opens Oracle's Account Management through the public IntelliJ settings API. Oracle owns sign-in and credential storage; the setup controller has no project-file writer. Live setup/cancellation and cross-tool identity parity remain release checks.
- **Open in NetSuite** accepts only user-supplied HTTPS account-specific `app.netsuite.com` base URLs, with no credentials, explicit port, path beyond `/`, query or fragment. It never derives a host from an account ID and never changes the SDF default or browser role.
- Display style, provider choice, explicit Node/launcher paths and account URL mappings are stored in application-local IDE preferences with Settings Sync disabled, not in `project.json`. These settings can contain local paths and account-identifying URLs; do not include the settings file in public reports.
- The plugin performs no automatic analytics or telemetry.

The plugin registers JetBrains Marketplace's built-in exception reporter. Reports can be submitted manually or automatically according to WebStorm's error-reporting preferences. JetBrains may receive diagnostic metadata, stack traces, user comments, and user-selected attachments. This IDE-managed reporting is separate from plugin analytics; review the IDE's reporting settings to control automatic submissions. See [JetBrains error-handler documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html).

## Dependency considerations

The 0.3.0 candidate uses a separately installed SuiteCloud CLI for Node.js by default, with an explicitly selectable legacy Java provider. Install trusted tooling and keep its runtimes access-controlled and compatible with Oracle's requirements. Oracle ended Java CLI support at version 2025.2 and states that downloads remain available only until NetSuite 2027.1; this does not assert that existing installations stop running. See [Oracle's lifecycle notice](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1489072226.html).

The plugin does not redistribute SuiteCloud CLI or its credential store. The CLI inherits the IDE's OS user and authentication environment and manages its own storage and runtime settings. Oracle's WebStorm plugin manages the separate setup flow. Synthetic tests establish plugin behavior, not live parity across those tools or authentication modes; see [compatibility evidence and remaining gates](docs/NODE-CLI-COMPATIBILITY.md).
