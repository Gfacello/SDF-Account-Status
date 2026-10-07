# Goal: prepare NetSuite SDF Account Status 0.3.0 for release

Status (2026-10-07): PRs #3–#11 are included in `main` at `17f8cc5`, the early testing ZIP/tag was delivered, and the superseded PRs are closed. The user accepted the account-loading fix at `caeb04b` and now explicitly requests pushing it, opening and merging a PR, and publishing the corrected build as **0.3.0**. This supersedes the earlier publication pause. Final merge, CI, artifact, corrected tag and public-release results must be verified; the detailed manual/live matrix retains its unobserved cases.

## Objective and completion

Consolidate and merge the 0.3.0 PRs, build the local testing ZIP and create the release tag following [PLAN-0.3.0.md](PLAN-0.3.0.md). Include Refresh, Richer status bar, Open in NetSuite, Environment and role filters, all four selected refactors, their regression tests, and corrected error-reporting documentation.

Keep the implemented Node.js CLI migration and Add an account flow in the testing build. Node is the default provider, with explicit legacy Java selection and Oracle Account Management setup. Live compatibility remains unverified and must be checked during user testing before publication; do not silently remove either feature.

The current goal is complete when the selected work and release preparation are merged into `main`, superseded PRs are closed, final automated checks pass, a ZIP built from the verified final revision is delivered with its checksum, and immutable `v0.3.0` points to that revision. Publish the corrected GitHub release after the final checks. The user has accepted the local loading fix; record other pending manual/live checks explicitly and do not count them as passed. The user has now authorized public release; execution and any publishing prerequisites remain recorded in the current release handoff. See [RELEASING.md](RELEASING.md).

Evidence, exact revisions and unchecked manual scenarios are recorded in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md). Test counts from different branches overlap and are not additive.

## Git strategy

- Keep `main` as the integration branch, with focused short-lived `research/...`, `refactor/...`, `feature/...` and `docs/...` PRs. Dependent branches are acceptable while prerequisites are under review.
- Use `feature/0.3.0-candidate` to assemble and validate the combined work. The candidate branch is not a published version or proof that its component PRs have been approved or merged.
- Use `chore/release-0.3.0` for the final version, changelog, Marketplace notes and release-preparation PR.
- Merge #11 into the candidate before merging #10 into `main`. Close the fully included PRs #3–#9 as superseded.
- The October 7 instruction authorizes the immutable `v0.3.0` tag after the final `main` automated checks and ZIP inspection, before the user's manual testing. Follow the release guide's signing policy with the subsequently authorized correction of the early testing tag described in [RELEASING.md](RELEASING.md).
- Introduce `release/0.3.0` only if later-version work needs to proceed while 0.3.0 stabilizes; add its push CI coverage and carry fixes back to `main` if used.
- Preserve other ongoing work; do not reset shared checkouts, rewrite shared history or move published release tags. The one-time early 0.3.0 testing-tag correction is recorded in the release guide.

This follows [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow). CI runs on PRs and `main`/`master` pushes. Its artifact upload runs even after a failed job, so an uploaded ZIP is not a passing verification result.

## Preserved history

The initial 2026-09-21 planning snapshot had `main`, `origin/main` and `v0.2.0` at `a81de01`, with an uncommitted Add an account prototype. That prototype was preserved as `a776bbe` and the plan on `docs/0.3.0-plan`. The user requested a review pause; work was paused, later explicitly resumed, and committed branches were pushed following the user's push request. Those earlier paused/planning status descriptions are historical, not the current execution state.

The temporary `/private/tmp` implementation checkouts subsequently disappeared. Committed work was recovered from the retained branches into `.worktrees/candidate`; integration and review fixes now live on `feature/0.3.0-candidate`. The original prototype branch remains preserved. This recovery does not claim that missing uncommitted temporary files were recovered byte-for-byte.

## Implementation and validation record

The entries below preserve completed implementation and revision-specific evidence from the earlier review phase. Unchecked manual/live items remain open for post-delivery testing under the October 7 order; historical draft references do not require keeping superseded PRs open.

### 1. Preserve work and establish the baseline

- [x] Preserve the original Add an account prototype on `feature/add-account` at `a776bbe` and planning work separately on `docs/0.3.0-plan`.
- [x] Refresh remote refs and establish the original source base `a81de01`; the implementation planning base `4ea28e9` had unchanged 0.2.0 source.
- [x] Freshly run baseline tests: 79 tests in nine suites, zero failures/errors; all 14 tasks executed on `4ea28e9`.
- [x] Run the baseline full gate on `58e2d14`: tests, packaging and both configured WebStorm verifiers passed. This is historical baseline evidence, not candidate evidence.
- [x] Push preserved committed branches and recover a durable candidate checkout on 2026-10-05.

Planning review: [PR #3](https://github.com/Gfacello/SDF-Account-Status/pull/3), draft.

### 2. Resolve the supported account-provider approach

- [x] Inspect documented Node `account:manageauth --list`/`--info` commands and Oracle formatter versions; record sanitized synthetic fixtures, runtime requirements and source provenance in [NODE-CLI-COMPATIBILITY.md](NODE-CLI-COMPATIBILITY.md).
- [x] Implement a direct Node executable/JavaScript launcher approach with optional absolute paths, Node as candidate default, explicit legacy Java selection and no automatic provider fallback.
- [x] Integrate the Oracle settings setup path through the public IntelliJ settings API, with automated cancellation, failure and missing-settings/controller coverage.
- [ ] Verify real account identity and role visibility across Node, legacy Java and Oracle WebStorm Account Management under matching OS user/authentication contexts.
- [ ] Demonstrate live Add an account completion/cancellation and project-default preservation on supported IDE installations.
- [ ] Finalize provider/setup compatibility scope from those results, or obtain an explicit user scope decision if the live gates cannot be demonstrated.

The user volunteered a dedicated test setup; the test authentication ID and setup details are still pending. No real account compatibility is claimed. Saved account URLs remain the browser design.

### 3. Refactor account workflows with regression tests

- [x] Extract account selection/workflow control and separate document persistence from widget construction.
- [x] Introduce a provider boundary returning common account results.
- [x] Cover production Confirm/Cancel, current-account selection, unrelated unsaved document edits, read-only files, failed saves, save-listener changes and success only after persistence.
- [x] Replace the cancellation setter test with actual cancel/reopen coverage restoring search, environment and role.

Implemented and covered by automated tests; see [PR #4](https://github.com/Gfacello/SDF-Account-Status/pull/4) and integrated evidence. Draft PR review remains outstanding.

### 4. Refactor state and presentation

- [x] Read/parse one project snapshot per refresh and derive the current identity and status from it, with change-during-read detection.
- [x] Publish coherent account-list state only after generation/disposal checks on the EDT.
- [x] Use one status-presentation function for normal refresh and successful switching, including rich style and metadata fallback.
- [x] Cover out-of-order completion, superseding loads, cancellation, disposal, external edits and consistent metadata with controllable scheduling and real EDT fixtures.

All four selected refactors are implemented. Optional optimization investigations remain outside scope.

### 5. Implement and validate the provider transition

- [x] Implement Node-specific discovery, direct process execution and complete list/info parsing behind the common provider boundary.
- [x] Cover empty/malformed responses, missing tooling, limits/timeouts, cancellation, same-account/different-role identities, explicit configuration and no automatic fallback.
- [x] Preserve direct `project.json` status display when account discovery is unavailable.
- [x] Update dependency, command scope, privacy, provider configuration and migration documentation to reflect the candidate.
- [x] Add focused macOS/Windows synthetic CLI CI jobs while retaining Ubuntu's complete verification job.
- [x] Record remote synthetic CLI execution on Windows and macOS: [PR #8 CI run](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37350903593) passed all three jobs on `0c8bc5c`, including Ubuntu full verification. This is provider/fixture branch evidence, not integrated candidate CI.
- [x] Record integrated candidate PR #10 CI: [run 37353758331](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37353758331) passed all three jobs for PR head `6787477`; the workflow tested merge revision `65537f21db98553b9cb2c7ad88d1f44801effdea`.
- [x] Record release PR #11 CI: [run 37360084211](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37360084211) passed all three jobs for head `3eba7aa`, with workflow artifact merge revision `0690daec031e3f7f149214e0751f1bbcc9cca944`.
- [ ] Observe CI for the final PR revision after subsequent documentation changes.
- [ ] Complete live provider parity/runtime verification, or obtain an explicit alternative-scope decision.

Review: [PR #8](https://github.com/Gfacello/SDF-Account-Status/pull/8), draft. The focused local macOS run passed 28 tests on `0c8bc5c`; this is synthetic evidence.

### 6. Deliver the selected UI features

Implementation and automated coverage are complete for the following items; manual acceptance checks remain in phase 7:

- [x] Refresh reloads accounts, retains valid search/environment/role filters and never writes the project default.
- [x] Environment and role filters combine with text search, retain grouping, show counts/empty states, and restore after refresh/production cancellation.
- [x] Rich status supports persisted display styles, exact-identity metadata, truncation, full tooltips/accessibility and unavailable metadata fallback.
- [x] Open in NetSuite uses validated local account URLs, correct current/selected-row targets and a browser controller without account-switching capability.
- [x] Adapt the preserved Add an account prototype to the Oracle settings/controller flow and cover its controller/picker behavior automatically.
- [ ] Pass Add an account's live end-to-end gate during testing before publication, or obtain an explicit scope decision if it fails.
- [x] Correct reporting wording in README, security/architecture docs and both plugin-description sources: IDE preferences control manual/automatic exception submissions.

Review: [PR #5 refresh/filters](https://github.com/Gfacello/SDF-Account-Status/pull/5), [PR #6 browser actions](https://github.com/Gfacello/SDF-Account-Status/pull/6), [PR #7 rich status](https://github.com/Gfacello/SDF-Account-Status/pull/7), [PR #9 reporting](https://github.com/Gfacello/SDF-Account-Status/pull/9). All are drafts. Add Account integration and cross-feature wiring are on the candidate branch.

### 7. Integrate and verify the candidate

- [x] Integrate the preserved branches in the durable candidate checkout; resolve merge conflicts and reviewed filter/context-click/save/configuration issues.
- [x] Pass an initial combined gate on `83c2695`: 213 tests in 29 suites, zero failures/errors/skips, packaging and configuration/structure checks, and Compatible results for WebStorm `261.22158.274` and `262.8665.259`.
- [x] Inspect that initial ZIP: correct `com.sdf.accountstatus` ID, packaged license and error handler, since-build `261`; artifact version remained `0.2.0`.
- [x] Pass the full gate on integrated source `8543334` after the provider filesystem-validation threading fix: 215 tests in 29 suites, zero failures/errors/skips, all 20 tasks executed, both WebStorm verifier verdicts Compatible.
- [x] Inspect the fresh `8543334` ZIP: correct ID, version `0.2.0`, since-build `261` with no upper bound, expected library JAR, packaged LICENSE and Marketplace exception handler; checksum recorded in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).
- [x] Inspect the `0.3.0` release-preparation ZIP from `c280bdd`: expected single library JAR, correct ID/version/build bounds, LICENSE, both icons and error handler; checksum recorded in the validation document.
- [x] Complete the install/load and missing/invalid-project recovery row in both IDE versions. Native 2026.1 showed missing-file and malformed-JSON states, then recovered to the restored production/Developer baseline. Native 2026.2 showed the same recovery sequence using synthetic `project262`, after a fresh session and Trust Project acceptance for that fixture only. External default edits in 262 also demonstrated the same account changing from Developer to Administrator and back.
- [ ] Complete the remaining supported-IDE manual matrix in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md), including actual widget lifecycle/multiple projects and provider changes during a load. Native 2026.1 demonstrated specific synthetic picker/filter/refresh and production Cancel/Confirm interactions. The 261 refresh row is also complete, including error/retry and vanished-role/current-account handling. Remaining compound rows stay open as recorded in the validation matrix. Input has been intermittent: raising the main 261 window allowed one picker session, but later picker/context actions again had no visible effect. Licensing readiness is unconfirmed.
- [ ] Record live provider/setup observations, sanitized configuration and any remaining limitations.
- [ ] Resolve review findings and required checks before the authorized integration into `main`.

Required gate: `./gradlew clean test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin`.

### 8. Consolidate, merge and deliver the testing build

Merge path: [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), `chore/release-0.3.0` → `feature/0.3.0-candidate`, then [PR #10](https://github.com/Gfacello/SDF-Account-Status/pull/10) → `main`. Earlier source/artifact evidence is at `c280bdd`; final merge and artifact evidence must be recorded separately.

- [x] Set version `0.3.0` and prepare candidate changelog/Marketplace notes with preparation date 2026-10-05.
- [x] Date the 0.3.0 preparation notes 2026-10-07 and explicitly retain pending manual/live validation and unpublished Marketplace status.
- [x] Align candidate provider prerequisites, local configuration, setup instructions and privacy documentation with the implementation; live compatibility limits remain explicit.
- [x] Pass the complete local gate on release-preparation source `c280bdd`: 215 tests in 29 suites, zero failures/errors/skips, all 20 tasks executed in 33 seconds, both configured WebStorm verifier verdicts Compatible.
- [x] Record passing remote CI for release PR #11 head `3eba7aa`; the workflow artifact was built from its GitHub merge revision, as recorded in the validation document.
- [ ] Observe fresh CI after documentation updates before approval.
- [x] Create and attach draft [release-preparation PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), targeting the candidate branch, with revision-specific evidence and unresolved gates.
- [x] Update [RELEASE-0.3.0.md](RELEASE-0.3.0.md) for the authorized merge → ZIP → tag → user testing order; public publication remains separate.
- [ ] Verify PRs #3–#9 are included, resolve findings, merge #11 then #10, and close superseded PRs.
- [ ] Verify final `main` CI and run the full automated gate from a clean checkout of the merged revision.
- [ ] Inspect and deliver the fresh ZIP with source revision, checksum and signing status.
- [ ] Create and push immutable `v0.3.0` at that verified revision under the release guide's signing policy.
- [ ] Prepare an unpublished GitHub release draft with the exact testing ZIP and pending-test notes.
- [ ] Hand off the remaining manual/live checks and local installation instructions to the user.

## Progress tracking

Check an item only when its observable result is verified. Keep implementation, synthetic tests, manual IDE behavior and live account compatibility distinct. Preserve the selected scope until the user makes an explicit scope decision. The validation record is the durable location for revision-specific results and still-unchecked scenarios.
