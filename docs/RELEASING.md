# Releasing

This checklist produces a reviewable, versioned NetSuite SDF Account Status distribution. Publishing changes external state; run the publish step only from the intended release commit and with authorized Marketplace credentials.

## Prerequisites

- JDK 21
- A clean Git worktree on the release branch
- A green CI run for the release commit
- JetBrains Marketplace publishing access
- Signing certificate chain, private key, and private-key password for signed releases
- Marketplace token for publishing

Never store signing material or Marketplace tokens in the repository, Gradle properties, shell history, build logs, or release attachments.

## 1. Prepare the version

1. Set `version` in `build.gradle.kts` using semantic versioning.
2. Move the release notes from `CHANGELOG.md`'s **Unreleased** section into a dated version section.
3. Confirm the `changeNotes` in `build.gradle.kts` describe that same version.
4. Update user/developer documentation for changed behavior or requirements.
5. Check that examples, fixtures, screenshots, and reports contain only synthetic account data.
6. Confirm the permanent plugin ID remains `com.sdf.accountstatus`.

## 2. Run the release gate

```bash
./gradlew clean test buildPlugin \
  verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
git diff --check
git status --short
```

All tasks must pass. Review the Plugin Verifier reports under `build/reports/pluginVerifier/` and the test report under `build/reports/tests/test/`.

The unsigned distribution is created at:

```text
build/distributions/netsuite-sdf-account-status-<version>.zip
```

Inspect the archive without extracting it into the repository:

```bash
unzip -t build/distributions/netsuite-sdf-account-status-<version>.zip
unzip -l build/distributions/netsuite-sdf-account-status-<version>.zip
shasum -a 256 build/distributions/netsuite-sdf-account-status-<version>.zip
```

Confirm that it contains one plugin directory, its `lib` directory, the versioned plugin JAR, `META-INF/plugin.xml`, both plugin icons, and `META-INF/LICENSE`.

## 3. Smoke test

Install the ZIP through **Settings | Plugins | Install Plugin from Disk** in each supported WebStorm release and verify:

- Missing, invalid, and valid `project.json` states
- Sandbox, production, and unknown status treatments
- Loading, empty, malformed-output, failure, and timeout behavior
- Search by authentication ID, account name, account number, role, and environment
- Current and recommended grouping
- Successful canonical, legacy, and dual-key updates
- Read-only and failed-save behavior leaves the project file unchanged
- Keyboard navigation, accessible row names, light/dark themes, popup resizing, and **Open project.json**
- No raw CLI output appears in the IDE log or user-facing errors

Use only synthetic or dedicated non-customer data during release validation.

## 4. Sign the plugin

Provide the secrets through the release environment or protected CI secrets:

- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

Then run:

```bash
./gradlew signPlugin verifyPluginSignature
```

Review and checksum the signed archive produced under `build/distributions/`. Do not publish the unsigned ZIP when a signed artifact is available.

## 5. Commit and tag

Commit the version, changelog, documentation, and code as one reviewed release commit. After CI passes on that exact commit:

```bash
git tag -s v<version> -m "NetSuite SDF Account Status v<version>"
git push origin <release-branch>
git push origin v<version>
```

If signed Git tags are not configured, establish and document the project's accepted tag-signing policy before releasing. Do not silently replace an existing release tag.

## 6. Publish

Configure `PUBLISH_TOKEN` only in the protected publishing environment, then run:

```bash
./gradlew publishPlugin
```

Before making the Marketplace release public, verify the listing has:

- Vendor **gfacello** and the project URL
- WebStorm-only compatibility and the intended build range
- Source, issue tracker, security policy, and MIT license links
- SuiteCloud CLI for Java prerequisite and lifecycle limitation
- Independent-project disclaimer
- Correct 0.2.0 release notes

Create a matching GitHub release with the signed ZIP, SHA-256 checksum, and concise notes. Never attach credentials, raw CLI output, local IDE logs, or verifier reports containing private paths/data.

## 7. After publishing

1. Install the Marketplace build in a clean supported WebStorm instance.
2. Confirm the Marketplace descriptor, vendor, version, compatibility, license, and download are correct.
3. Monitor user-submitted exceptions in JetBrains Marketplace and public issues.
4. If a critical problem is found, hide or remove the affected Marketplace version and publish a fixed patch; do not replace binaries under an existing version.
