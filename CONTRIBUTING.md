# Contributing

Contributions to NetSuite SDF Account Status are welcome. Keep changes focused, privacy-safe, and compatible with the supported WebStorm versions.

## Prerequisites

- JDK 21
- Git
- Network access for the first Gradle and WebStorm dependency download
- SuiteCloud CLI for Java only when manually exercising the account picker

The unit tests use synthetic CLI responses and do not require credentials or a local SuiteCloud installation.

## Set up the project

```bash
git clone https://github.com/Gfacello/SDF-Account-Status.git
cd SDF-Account-Status
./gradlew --version
./gradlew test
```

Import the repository as a Gradle project when opening it in an IntelliJ-based IDE.

## Development workflow

1. Create a focused branch.
2. Make the smallest cohesive change that solves the problem.
3. Add or update tests for observable behavior and error states.
4. Run the validation commands below.
5. Review `git diff` for accidental account data, credentials, generated files, and unrelated formatting.
6. Open a pull request describing the behavior change and verification performed.

Do not commit real authentication IDs, account IDs, company names, roles copied from a customer environment, credential files, tokens, private keys, or raw SuiteCloud CLI output. Use clearly synthetic fixtures such as `example-sandbox`, `<ACCOUNT_ID>_SB1`, and `Example Company`.

## Validate changes

Run the fast checks during development:

```bash
./gradlew test
```

Before opening or merging a pull request, run the complete local gate:

```bash
./gradlew clean test buildPlugin \
  verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
```

Plugin Verifier downloads the configured WebStorm versions and can take longer on its first run. Continuous integration runs the same test, package, structure, and compatibility checks.

## Run a development IDE

```bash
./gradlew runIde
```

Use a synthetic `project.json` and non-customer authentication fixtures when recording screenshots or reproductions. Check keyboard navigation, screen-reader labels, loading/error states, light and dark themes, and both sandbox and production visual treatments.

## Build the plugin

```bash
./gradlew clean test buildPlugin
```

The versioned archive is written to:

```text
build/distributions/netsuite-sdf-account-status-<version>.zip
```

Install that ZIP with WebStorm's **Settings | Plugins | Install Plugin from Disk** action. Do not commit files from `build/`.

## Code and test guidelines

- Keep filesystem and process I/O out of the Event Dispatch Thread.
- Preserve the rule that only the read-only account-list CLI operation is executed.
- Never log or surface raw CLI output in an exception or user-facing error.
- Treat malformed output as an unavailable list rather than guessing.
- Keep `project.json` mutations inside an IDE write command and leave the file unchanged on validation or save failure.
- Prefer pure, focused tests for parsing and ranking plus IntelliJ platform tests for IDE document/UI behavior.
- Preserve valid JSON values unrelated to the authentication-ID keys.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component boundaries and [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Pull-request checklist

- [ ] Tests cover the new behavior and relevant failure states.
- [ ] The full Gradle validation gate passes.
- [ ] UI changes were checked in light and dark themes and with keyboard navigation.
- [ ] Documentation and `CHANGELOG.md` are updated when user-visible behavior changes.
- [ ] Fixtures, screenshots, logs, and docs contain no real customer/account data or credentials.
- [ ] The plugin ID `com.sdf.accountstatus` remains unchanged.

Release publishing is maintained separately; see [docs/RELEASING.md](docs/RELEASING.md).
