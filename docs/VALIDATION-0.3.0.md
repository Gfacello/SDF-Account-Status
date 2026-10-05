# 0.3.0 candidate validation

Updated 2026-10-05. Release-preparation source: `chore/release-0.3.0` at `c280bddb8d9ee4b44aa06f76a550d5813397e35d`, in the durable `.worktrees/candidate` checkout. The inspected artifact is version `0.3.0`, an unpublished candidate awaiting manual/live validation, exact release-revision CI and review. Changelog/Marketplace notes describe the candidate; the changelog deliberately remains **Unreleased candidate**, with preparation date 2026-10-05 rather than a publication date. Scope and completion criteria remain in [PLAN-0.3.0.md](PLAN-0.3.0.md) and [GOAL-0.3.0.md](GOAL-0.3.0.md).

## Automated evidence

| Revision/date | Evidence | Result and scope |
| --- | --- | --- |
| `c280bdd`, 2026-10-05 | Complete local gate below on macOS, JDK 21 | Passed: 215 tests in 29 suites; zero failures, errors or skips; all 20 Gradle tasks executed in 33 seconds |
| `c280bdd`, 2026-10-05 | Plugin Verifier | Compatible with WebStorm `261.22158.274` (2026.1) and `262.8665.259` (2026.2) |
| `c280bdd`, 2026-10-05 | Versioned ZIP inspection | ID `com.sdf.accountstatus`, version `0.3.0`, since-build `261`, no upper build bound; expected single library JAR, LICENSE, both plugin icons and `JetBrainsMarketplaceErrorReportSubmitter` present |
| `8543334`, 2026-10-05 | Earlier integrated source gate on macOS, JDK 21 | Passed: 215 tests in 29 suites; zero failures, errors or skips; all 20 Gradle tasks executed in 45 seconds |
| `8543334`, 2026-10-05 | Plugin Verifier | Compatible with WebStorm `261.22158.274` (2026.1) and `262.8665.259` (2026.2) |
| `8543334`, 2026-10-05 | Historical pre-version-bump ZIP inspection | ID `com.sdf.accountstatus`, version `0.2.0`, since-build `261`, no upper build bound; expected library JAR, LICENSE and `JetBrainsMarketplaceErrorReportSubmitter` present |
| `83c2695`, 2026-10-05 | Prior combined gate before final provider threading fix | Passed 213 tests in 29 suites, packaging/configuration/structure and both WebStorm verifiers; superseded by the later revision-specific gates above |
| `0c8bc5c`, 2026-10-05 | [PR #8 GitHub Actions run 37350903593](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37350903593) | Success for all three jobs: Ubuntu full verification, macOS focused synthetic CLI tests, Windows focused synthetic CLI tests |
| `0c8bc5c`, 2026-10-05 | Local focused `NodeSuiteCloud*Test` run | Passed 28 tests across four suites on macOS/JDK 21; zero failures/errors/skips |

The remote OS run validates synthetic provider/process fixtures on `0c8bc5c`, not the integrated candidate's UI or a real Oracle CLI/account. Only the Unix npm symlink fixture is disabled on Windows; Windows npm-wrapper discovery and subprocess checks remain enabled. Existing GitHub Actions v4 deprecation warnings were nonfatal. These suites overlap; counts are not additive.

The current source gate ran:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
./gradlew clean test buildPlugin \
  verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin \
  --offline --console=plain \
  -Dsun.net.client.defaultConnectTimeout=5000 \
  -Dsun.net.client.defaultReadTimeout=5000
```

Local evidence locations, relative to the candidate checkout unless shown as absolute:

- Test results: `build/test-results/test/TEST-*.xml` and `build/reports/tests/test/`.
- Verifier verdicts: `build/reports/pluginVerifier/WS-261.22158.274/plugins/com.sdf.accountstatus/0.3.0/verification-verdict.txt` and corresponding `WS-262.8665.259` path.
- ZIP: `build/distributions/netsuite-sdf-account-status-0.3.0.zip`.
- Release-preparation gate log: `/private/tmp/sdf-030-release-gate.log`.
- Historical `8543334` gate log: `/private/tmp/sdf-030-candidate-final-gate.log`.
- Prior combined gate log: `/private/tmp/sdf-030-candidate-gate.log`.
- Focused local CLI log: `/private/tmp/sdf-030-cli-platforms-tests.log`.

Current inspected **0.3.0** ZIP from `c280bdd`, SHA-256:

```text
3b56bc27be448c54e61833474b77197133e6c7c6e0984b7be628d5b908d40320
```

Historical **0.2.0-labelled** development ZIP from `8543334`, SHA-256:

```text
367c6d0e3636440bde864117cf3fa65e1592345afbaccb7d81539878d63c929c
```

These are distinct artifacts; the historical checksum is not the release-preparation ZIP checksum.

Generated results and temporary logs are not committed and may be replaced by later runs. This record retains their observed result and exact revision; record new revision-specific evidence when preparing the versioned release artifact.

The integrated suite covers workflow/gateway persistence, real IntelliJ document and EDT behavior, snapshot/load races, filters/refresh and production-cancel restoration, browser targeting/URL validation, rich presentation/local preferences, provider selection and settings, setup-controller effects, and bounded synthetic subprocess execution. Automated component coverage does not prove the complete manual scenarios below.

## Manual IDE matrix — not yet verified

An isolated synthetic WebStorm 2026.1 process was launched on 2026-10-05, but the UI automation tool could not attach to its development Java application. The process was stopped; no completed manual observation is claimed. The standard `runIde` task also encountered an unavailable optional Compose hot-reload agent (`1.1.0-alpha03`); the attempted smoke session disabled that unused agent through a local init script. These are development-environment limitations, not passing UI checks. A native signed-app launch alternative is being investigated; no new manual pass has been established.

Each box below remains open until observed in that IDE version. Record source/ZIP checksum, IDE build, OS, Oracle plugin version when relevant, and concise results. Use synthetic data or the dedicated test setup; exclude credentials and customer identifiers from evidence.

| Scenario | WebStorm 2026.1 | WebStorm 2026.2 |
| --- | --- | --- |
| Install/load candidate; widget appears; missing/invalid `project.json` states recover after correction | [ ] | [ ] |
| Mouse and keyboard picker activation; Enter on groups/accounts; context menu/Control-click does not switch accounts | [ ] | [ ] |
| Search + environment + role combine; clear/no-results/counts; full restoration after cancelled production confirmation | [ ] | [ ] |
| Refresh loading/success/error/retry; retained filters; vanished-role explanation; removed-current default stays unchanged | [ ] | [ ] |
| Production Confirm/Cancel; successful save; read-only/save failure stays actionable without stale displayed default | [ ] | [ ] |
| Both status styles; long names/roles; full tooltip/accessibility; exact role metadata after external default edits | [ ] | [ ] |
| Light/dark themes, minimum/narrow picker size, visible footer controls, keyboard focus and screen-reader labels | [ ] | [ ] |
| Preferences persist across restart; multiple open projects respond to display/provider changes | [ ] | [ ] |
| Change provider/paths during an active load; old result cannot replace new provider state; cancellation preserves configuration | [ ] | [ ] |
| Close/reopen picker during a load; close project/dispose widget during load; no late UI updates or leaked process | [ ] | [ ] |
| Browser first-use/edit/cancel/invalid URL dialogs; current vs selected-row target; production/sandbox mappings; launch leaves project default unchanged | [ ] | [ ] |
| Add Account while loaded/loading/unavailable; Oracle settings opens correctly; return refreshes and clears filters | [ ] | [ ] |
| Cancel Oracle setup/settings; missing/disabled Oracle plugin; unavailable settings page; restored picker and useful guidance | [ ] | [ ] |

Browser checks establish navigation and target selection, not automatic login or CLI role selection. Binary Plugin Verifier compatibility is not manual UI verification.

## Live provider and setup gate — pending dedicated setup

The user volunteered a dedicated non-customer test setup; the authentication ID and setup details are pending. No real Oracle account-list/info, shared identity parity or authenticated Add Account result is claimed by the synthetic evidence above.

- [ ] Record Node/JDK/CLI/Oracle plugin/IDE versions and the matching OS user/authentication mode/secure-storage context, without secrets.
- [ ] Run supported Node `account:manageauth --list` and `--info` for the dedicated identity; record sanitized metadata and role completeness.
- [ ] Compare the same identity and account/role meaning in Node, legacy Java and Oracle WebStorm Account Management; distinguish browser and machine-to-machine contexts.
- [ ] Add a dedicated identity through the button, return to the picker, and verify it is visible to the selected provider.
- [ ] Verify completion and cancellation both preserve the project's previous default and unrelated contents; select the new default separately, with production confirmation when applicable.
- [ ] Exercise the real Node/Java process tree and runtime discovery from supported IDE launch environments; synthetic subprocess execution is insufficient for this claim.
- [ ] If these gates cannot be demonstrated, obtain an explicit user scope decision before changing release inclusion or calling the goal complete.

Research provenance and detailed output/process limits are in [NODE-CLI-COMPATIBILITY.md](NODE-CLI-COMPATIBILITY.md).

## Review and release-preparation status

Focused draft PRs are available and attached to the task:

| PR | Scope |
| --- | --- |
| [#3](https://github.com/Gfacello/SDF-Account-Status/pull/3) | Planning and release workflow |
| [#4](https://github.com/Gfacello/SDF-Account-Status/pull/4) | Workflow/state refactors and regression tests |
| [#5](https://github.com/Gfacello/SDF-Account-Status/pull/5) | Refresh and environment/role filters |
| [#6](https://github.com/Gfacello/SDF-Account-Status/pull/6) | Open in NetSuite |
| [#7](https://github.com/Gfacello/SDF-Account-Status/pull/7) | Rich status display |
| [#8](https://github.com/Gfacello/SDF-Account-Status/pull/8) | Node provider and synthetic OS CI |
| [#9](https://github.com/Gfacello/SDF-Account-Status/pull/9) | Error-reporting documentation |
| [#10](https://github.com/Gfacello/SDF-Account-Status/pull/10) | Integrated candidate targeting `main` |

All are drafts; creation is not approval or merging. Integrated Add Account/provider configuration and review fixes are on the candidate branch. The dependent release-preparation PR is being prepared; its confirmed URL is still pending. Earlier committed implementation branches were pushed; the candidate worktree is durable after the temporary checkouts disappeared.

- [x] Create `chore/release-0.3.0`, set version `0.3.0`, and prepare candidate changelog/Marketplace notes at `c280bdd`.
- [x] Pass the complete local gate for `c280bdd`, inspect its versioned ZIP and record its checksum.
- [x] Prepare the [post-merge release checklist](RELEASE-0.3.0.md), including final-main CI, clean checkout, signing, checksum, immutable tag and publication actions.
- [ ] Record passing remote CI for the exact release-preparation revision.
- [ ] Complete manual/live evidence and resolve review findings; only then finalize publication-date notes and verified provider/setup inclusion.
- [ ] Create and record the release-preparation PR URL, then complete the review handoff.
- [ ] Hand off the reviewable candidate. Do not merge the release-preparation PR, push a release tag or publish Marketplace/GitHub releases without subsequent authorization.
