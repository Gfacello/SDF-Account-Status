# Releasing

This checklist produces a reviewable, versioned NetSuite SDF Account Status distribution. Publishing changes external state; run the publish step only from the intended release commit and with authorized Marketplace credentials.

## Branches and version tags

- `main` contains integrated, tested code for the next release. Make feature and refactor changes on short-lived branches and merge them through reviewed PRs.
- Use `chore/release-<version>` for the final version and release-documentation PR. For 0.3.0, use `chore/release-0.3.0`.
- A `v<version>` tag identifies the exact versioned commit. Keep published tags immutable. Tagging a build for local acceptance testing does not imply Marketplace publication or completion of manual checks.
- Create a separate `release/<version>` stabilization branch only if later-version development must continue before the release ships. Add push CI coverage for that branch and carry stabilization fixes back to `main`.
- A permanent `develop` branch is not part of this workflow.

For the current implementation sequence and goal progress, see [GOAL-0.3.0.md](GOAL-0.3.0.md). The approach follows [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow).

## Prerequisites

- JDK 21
- A clean Git worktree containing the intended candidate commit
- A green CI run for the release commit
- JetBrains Marketplace publishing access
- Signing certificate chain, private key, and private-key password for signed releases
- Marketplace token for publishing

Never store signing material or Marketplace tokens in the repository, Gradle properties, shell history, build logs, or release attachments.

## 1. Prepare the version

After the selected feature/refactor PRs have been reviewed and integrated, create `chore/release-<version>` from the intended `main` commit. Preserve and checkpoint any unrelated uncommitted work before changing branches.

1. Set `version` in `build.gradle.kts` using semantic versioning.
2. Move the release notes from `CHANGELOG.md`'s **Unreleased** section into a dated version section.
3. Confirm the `changeNotes` in `build.gradle.kts` describe that same version.
4. Update user/developer documentation for changed behavior or requirements.
5. Check that examples, fixtures, screenshots, and reports contain only synthetic account data.
6. Confirm the permanent plugin ID remains `com.sdf.accountstatus`.

## 2. Validate the release-preparation PR

```bash
./gradlew clean test buildPlugin \
  verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
git diff --check
git status --short
```

All tasks must pass. Review the Plugin Verifier reports under `build/reports/pluginVerifier/` and the test report under `build/reports/tests/test/`.

Commit the version, changelog, Marketplace notes, and documentation as a focused release-preparation change, then open its PR to `main`. Feature work remains in its own reviewed commits/PRs. Require the PR checks and review to pass before merging.

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

## 4. Merge and identify the final release commit

After release review authorizes the merge, merge the release-preparation PR and record the resulting `main` commit SHA. Wait for successful CI on that exact SHA. A passing PR merge-test result alone does not establish that the final merged commit passed.

The current workflow runs on PRs and `main`/`master` pushes; tag pushes do not trigger it. Artifacts are uploaded even when a job fails, so an available ZIP is not evidence of a passing gate.

## 5. Build, sign, and tag the final candidate

Use a clean isolated checkout of the recorded final SHA. Run the complete release gate from step 2, inspect the final archive, and verify the installed final candidate. If a source change is needed, submit it through review and repeat these steps for the new final SHA.

Provide signing secrets through the release environment or protected CI secrets:

- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

Then sign and verify:

```bash
./gradlew signPlugin verifyPluginSignature
```

Review the signed archive under `build/distributions/`, smoke-test that final ZIP, and record its SHA-256 and source commit. Preserve those exact bytes for publication. Do not publish the unsigned ZIP when a signed artifact is available.

When tagging is authorized, prefer a signed annotated tag if a signing identity is configured. Create it on the recorded SHA and push only that release tag:

```bash
git tag -s v<version> <release-commit-sha> -m "NetSuite SDF Account Status v<version>"
git push origin v<version>
```

For the 0.3.0 local-testing handoff authorized on 2026-10-07, no Git signing identity is configured. The accepted repository policy permits an annotated unsigned tag (`git tag -a`) for this handoff, recording the exact source SHA and ZIP checksum in the draft GitHub release. The existing `v0.2.0` tag is also unsigned. This exception does not waive artifact signing for Marketplace publication. Prefer signed tags once an identity is configured.

On 2026-10-07, the user accepted the loading fix and explicitly requested that it ship as 0.3.0 instead of 0.3.1. The early `v0.3.0` local-testing tag at `17f8cc5` had no published GitHub release. This one-time correction replaces that testing tag with the final verified merge commit before the first public 0.3.0 release. Preserve the old tag object locally, verify the remote tag still matches the expected testing tag, and use a lease for the replacement. Once the public release is published, `v0.3.0` is immutable. The GitHub ZIP may be distributed unsigned with an explicit signing-status note; Marketplace signing and upload must be recorded separately.

## 6. Publish

Configure `PUBLISH_TOKEN` only in the protected publishing environment, then run:

```bash
./gradlew publishPlugin
```

Before executing the task, verify that its configured input is the signed archive from step 5. Do not let publication substitute a newly rebuilt or different archive without repeating artifact verification. Attach the same signed ZIP and checksum to the GitHub release for the tag.

Before making the Marketplace release public, verify the listing has:

- Vendor **gfacello** and the project URL
- WebStorm-only compatibility and the intended build range
- Source, issue tracker, security policy, and MIT license links
- Correct prerequisites and lifecycle notes for the CLI provider(s) actually supported by this release
- Independent-project disclaimer
- Release notes matching the published version and delivered behavior

Create a matching GitHub release with the signed ZIP, SHA-256 checksum, and concise notes. Never attach credentials, raw CLI output, local IDE logs, or verifier reports containing private paths/data.

## 7. After publishing

1. Install the Marketplace build in a clean supported WebStorm instance.
2. Confirm the Marketplace descriptor, vendor, version, compatibility, license, and download are correct.
3. Monitor user-submitted exceptions in JetBrains Marketplace and public issues.
4. If a critical problem is found, hide or remove the affected Marketplace version and publish a fixed patch; do not replace binaries under an existing version.
