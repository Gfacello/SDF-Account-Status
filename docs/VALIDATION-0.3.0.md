# 0.3.0 candidate validation

Updated 2026-10-05. Release-preparation source: `chore/release-0.3.0` at `c280bddb8d9ee4b44aa06f76a550d5813397e35d`, in the durable `.worktrees/candidate` checkout. Subsequent observed release head `ca819ac74796b432776b8688897cf0a6c2f30b88` changes documentation only; draft [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11) targets `feature/0.3.0-candidate`. The local source gate and checksum below remain associated with `c280bdd`, not a new run of the documentation commit. The inspected artifact is version `0.3.0`, an unpublished candidate awaiting full manual/live validation and review. The latest observed PR #11 CI passed for head `ca819ac`; documentation updates require fresh CI observation before approval, without carrying forward an earlier revision’s result. Changelog/Marketplace notes describe the candidate; the changelog deliberately remains **Unreleased candidate**, with preparation date 2026-10-05 rather than a publication date. Scope and completion criteria remain in [PLAN-0.3.0.md](PLAN-0.3.0.md) and [GOAL-0.3.0.md](GOAL-0.3.0.md).

## Automated evidence

| Revision/date | Evidence | Result and scope |
| --- | --- | --- |
| PR #11 head `ca819ac`, 2026-10-05 | [GitHub Actions run 37356754447](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37356754447) | Success for all three jobs: Ubuntu full gate, macOS and Windows synthetic CLI tests. Workflow artifact merge revision: `ff77d6e8b9adb74465ac92ea99d980a747fa9bc6` |
| PR #11 head `bdc9c00`, 2026-10-05 | [GitHub Actions run 37355038442](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37355038442) | Success for all three jobs: Ubuntu full gate (3m 52s), macOS and Windows synthetic CLI tests; total run 5m 0s. Workflow artifact merge revision: `b40d0f80a4a62c672ab9dab2ad8107a80e846e1f` |
| PR #10 head `6787477`, 2026-10-05 | [GitHub Actions run 37353758331](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37353758331) | Success for all three jobs: Ubuntu full gate (4m 55s), macOS and Windows synthetic CLI tests; total run 6m 11s. Workflow artifact/checkout merge revision: `65537f21db98553b9cb2c7ad88d1f44801effdea` |
| `c280bdd`, 2026-10-05 | Complete local gate below on macOS, JDK 21 | Passed: 215 tests in 29 suites; zero failures, errors or skips; all 20 Gradle tasks executed in 33 seconds |
| `c280bdd`, 2026-10-05 | Plugin Verifier | Compatible with WebStorm `261.22158.274` (2026.1) and `262.8665.259` (2026.2) |
| `c280bdd`, 2026-10-05 | Versioned ZIP inspection | ID `com.sdf.accountstatus`, version `0.3.0`, since-build `261`, no upper build bound; expected single library JAR, LICENSE, both plugin icons and `JetBrainsMarketplaceErrorReportSubmitter` present |
| `8543334`, 2026-10-05 | Earlier integrated source gate on macOS, JDK 21 | Passed: 215 tests in 29 suites; zero failures, errors or skips; all 20 Gradle tasks executed in 45 seconds |
| `8543334`, 2026-10-05 | Plugin Verifier | Compatible with WebStorm `261.22158.274` (2026.1) and `262.8665.259` (2026.2) |
| `8543334`, 2026-10-05 | Historical pre-version-bump ZIP inspection | ID `com.sdf.accountstatus`, version `0.2.0`, since-build `261`, no upper build bound; expected library JAR, LICENSE and `JetBrainsMarketplaceErrorReportSubmitter` present |
| `83c2695`, 2026-10-05 | Prior combined gate before final provider threading fix | Passed 213 tests in 29 suites, packaging/configuration/structure and both WebStorm verifiers; superseded by the later revision-specific gates above |
| `0c8bc5c`, 2026-10-05 | [PR #8 GitHub Actions run 37350903593](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37350903593) | Success for all three jobs: Ubuntu full verification, macOS focused synthetic CLI tests, Windows focused synthetic CLI tests |
| `0c8bc5c`, 2026-10-05 | Local focused `NodeSuiteCloud*Test` run | Passed 28 tests across four suites on macOS/JDK 21; zero failures/errors/skips |

PR #10's successful run belongs to head `6787477d566d395b6564c36a85db206a90f37aa7`, but the pull-request workflow artifact/checkout uses GitHub's merge revision `65537f21db98553b9cb2c7ad88d1f44801effdea`. Do not label that artifact as a build of the PR head alone or as release PR #11 evidence. The latest observed [PR #11 run 37356754447](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37356754447) passed all three jobs for head `ca819ac`; its artifact uses merge revision `ff77d6e8b9adb74465ac92ea99d980a747fa9bc6`. The earlier `bdc9c00` success remains historical evidence in the table. Each result applies to its recorded revision; observe fresh checks after documentation updates before approval. The earlier PR #8 remote run validates its own provider/fixture branch; these runs use synthetic CLI processes, not real Oracle accounts or manual IDE interaction. Only the Unix npm symlink fixture is disabled on Windows; Windows npm-wrapper discovery and subprocess checks remain enabled. Existing GitHub Actions v4 deprecation warnings were nonfatal. These suites overlap; counts are not additive.

The `c280bdd` release-preparation source gate ran:

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

## Manual IDE matrix — incomplete

### Partial manual observations

The native signed WebStorm sessions on 2026-10-05 used the unchanged `c280bdd` version `0.3.0` ZIP and SHA-256 recorded above, on macOS, with synthetic accounts and separate test profiles. These observations establish only the individual results below; no complete broad matrix row is marked passed.

**WebStorm 2026.1 (`WS-261.22158.274`).** The plugin loaded, and accessibility output plus a screenshot showed a green `example-sandbox-dev` status with **Example Company**, account ID `123456_SB1`, **Sandbox (SB1)** and role **Developer**. A fresh session at 15:35:01 after the local harness fixes demonstrated these interactions:

| Action | Observed result |
| --- | --- |
| Open picker; search `production`; then add Sandbox environment | Counts changed from `5/5` to `1/5` to `0/5`. |
| Clear search; select Sandbox + Administrator | Exactly `example-sandbox-admin` matched (`1/5`). |
| Refresh with those filters | While Reading was shown, Refresh, the table and filter controls were disabled; Add an account stayed enabled. Completion retained Sandbox + Administrator and `1/5`. The project default stayed `example-sandbox-dev`, with the unrelated fixture value preserved. |
| Add an account with Oracle’s plugin missing; acknowledge guidance | Guidance directed installation/enabling through Settings / Plugins. The picker reopened with Sandbox + Administrator retained. No real account setup occurred. |
| Clear filters; set query `example`, Production + Developer; press Down then Enter | Exactly one account matched; the production-confirmation dialog identified account `123456`, role Developer. |
| Cancel production confirmation | Query `example`, Production and Developer were restored, still `1/5`; the project file stayed unchanged. |
| Repeat Down then Enter and confirm | Status changed to `example-production`, with Production / `PROD` metadata and Developer role. Disk inspection showed only the default authentication ID changed; the unrelated fixture value was preserved. |

The later widget context menu was not observed, and UI input became unreliable with accessibility timeouts and a FUS worker waiting on remote configuration. No plugin stack frames were found in the inspected log; a plugin defect was not established. In a subsequent isolated 261 session, temporary statistics-disable flags were used and the previously logged FUS stall was no longer observed. This does not prove that the flags caused the change: a physical-coordinate left click opened the picker once, but context menus and later input remained unverified or ineffective. A malformed synthetic response was briefly prepared and restored without an observed UI error path, so refresh error/retry remains untested manually. The flags were confined to test-profile VM options; personal IDE/privacy settings were unchanged.

**WebStorm 2026.2 (`WS-262.8665.259`).** The native launcher loaded plugin `0.3.0` and opened the isolated `project262` frame. Accessibility output and a screenshot showed the synthetic sandbox status and metadata: **Example Company**, `123456_SB1`, **Sandbox (SB1)** and **Developer**. Accessibility clicks had no visible effect, and coordinate interaction returned `noWindowsAvailable`; picker/input behavior was therefore not validated in this version. The log continued to report licensing-facade waits and daemon retries, so licensing readiness is not established. The cause of a visible IDE error indicator was not identified; this session does not establish that the IDE was error-free.

The first native 261 attempt had also recorded **License required** and a JetBrains Station socket-path error; the test harness path was subsequently shortened. The earlier Gradle-launched synthetic process could not be attached through UI automation and was stopped. Its optional Compose hot-reload-agent issue was handled only in a local init script. These historical harness issues do not invalidate the later specific observations, establish a plugin defect, or prove startup/recovery acceptance. Native logs remain local, uncommitted evidence under `.worktrees/manual-fixture/native261/log/idea.log` and `.worktrees/manual-fixture/native262/log/idea.log` relative to the repository root; later sessions may replace them. Both test IDEs were closed after observation; the 262 log recorded normal shutdown at 15:56:23 and its process exit was verified.

Missing/invalid-project recovery, refresh error/retry, context/browser/provider/style dialogs, full accessibility/layout coverage, restart/multiple-project behavior and load/disposal races remain open as listed below. The observed missing-Oracle guidance does not satisfy live Oracle setup or cancellation. No live account authentication or complete manual acceptance is claimed.

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
| [#11](https://github.com/Gfacello/SDF-Account-Status/pull/11) | Release preparation targeting `feature/0.3.0-candidate` |

All are drafts; creation is not approval or merging. Integrated Add Account/provider configuration and review fixes are on the candidate branch. Dependent draft release-preparation [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11) has been created and attached; the latest observed CI for `ca819ac` passed, and its PR description was updated with that CI result and the verified 261 interactions. Fresh CI observation after documentation updates and review completion remain pending. Earlier committed implementation branches were pushed; the candidate worktree is durable after the temporary checkouts disappeared.

- [x] Create `chore/release-0.3.0`, set version `0.3.0`, and prepare candidate changelog/Marketplace notes at `c280bdd`.
- [x] Pass the complete local gate for `c280bdd`, inspect its versioned ZIP and record its checksum.
- [x] Prepare the [post-merge release checklist](RELEASE-0.3.0.md), including final-main CI, clean checkout, signing, checksum, immutable tag and publication actions.
- [x] Record passing release PR #11 CI for head `ca819ac` and its workflow merge revision `ff77d6e8b9adb74465ac92ea99d980a747fa9bc6`.
- [ ] Observe fresh CI after documentation updates before approval; do not carry a previous run's success onto an unobserved revision.
- [ ] Complete manual/live evidence and resolve review findings; only then finalize publication-date notes and verified provider/setup inclusion.
- [x] Create and attach draft [release-preparation PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), targeting the candidate branch. Its creation does not complete the remaining review/live/manual gates.
- [ ] Hand off the reviewable candidate. Do not merge the release-preparation PR, push a release tag or publish Marketplace/GitHub releases without subsequent authorization.
