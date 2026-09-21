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
- It executes only the local SuiteCloud authentication-list operation used by the account picker.
- CLI standard output and standard error remain in process memory and are not logged or included in user-facing failures.
- Malformed output, process failure, timeout, and an empty account list are treated as unavailable data.
- `project.json` is changed only after strict JSON validation and only through an IntelliJ write command.
- Selecting an account changes the supported authentication-ID field or fields; unrelated JSON values are retained.
- The plugin performs no automatic analytics or telemetry.
- **Add an account** opens Oracle's Account Management settings; Oracle's plugin owns the authentication flow, network requests, and credential storage. The account list is reloaded after settings close.

The plugin registers JetBrains Marketplace's built-in exception reporter. A report is transmitted only after a user explicitly reviews and submits it through WebStorm's error-reporting UI. JetBrains may receive diagnostic metadata, stack traces, user comments, and user-selected attachments according to that consent flow.

## Dependency considerations

The account picker currently integrates with a separately installed SuiteCloud CLI for Java. Oracle ended support for that CLI at version 2025.2 and plans to remove its download in NetSuite 2027.1. Keep the local installation patched and access-controlled, and follow the migration status documented in the project README.

The plugin does not redistribute SuiteCloud CLI or its credential store.
