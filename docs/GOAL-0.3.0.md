# Goal: prepare NetSuite SDF Account Status 0.3.0 for release

Status: active. On 2026-10-05 the committed branches were recovered into the durable `.worktrees/candidate` checkout and integrated on `feature/0.3.0-candidate`. The source revision `8543334` passed the complete local automated gate; manual/live validation is still pending. Version metadata is still `0.2.0`; the release-preparation branch and final review handoff remain outstanding. Nothing in this checklist authorizes publication, a release tag, or merging the release-preparation PR.

## Objective and completion

Prepare a reviewable 0.3.0 release candidate following [PLAN-0.3.0.md](PLAN-0.3.0.md). Include Refresh, Richer status bar, Open in NetSuite, Environment and role filters, all four selected refactors, their regression tests, and corrected error-reporting documentation.

Resolve Node.js CLI migration and Add an account compatibility. The candidate now implements Node as the default provider, explicit legacy Java selection, and Oracle Account Management setup, but those implementations do not replace the live compatibility gate. Obtain an explicit scope decision if migration or Add an account cannot be demonstrated; do not silently remove either feature.

The goal is complete only when the agreed implementation, documentation, passing validation evidence, recorded manual verification, focused reviewable PRs and a release-preparation PR are ready. Publishing, pushing a release tag, and merging the release-preparation PR are later actions requiring authorization. See [RELEASING.md](RELEASING.md).

Evidence, exact revisions and unchecked manual scenarios are recorded in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md). Test counts from different branches overlap and are not additive.

## Git strategy

- Keep `main` as the integration branch, with focused short-lived `research/...`, `refactor/...`, `feature/...` and `docs/...` PRs. Dependent branches are acceptable while prerequisites are under review.
- Use `feature/0.3.0-candidate` to assemble and validate the combined work. The candidate branch is not a published version or proof that its component PRs have been approved or merged.
- Use `chore/release-0.3.0` for the final version, changelog, Marketplace notes and release-preparation PR.
- Tag the final approved, validated `main` commit as immutable `v0.3.0` only when release authorization is given. Do not create the tag during feature development.
- Introduce `release/0.3.0` only if later-version work needs to proceed while 0.3.0 stabilizes; add its push CI coverage and carry fixes back to `main` if used.
- Preserve other ongoing work; do not reset shared checkouts, rewrite shared history or move existing release tags.

This follows [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow). CI runs on PRs and `main`/`master` pushes. Its artifact upload runs even after a failed job, so an uploaded ZIP is not a passing verification result.

## Preserved history

The initial 2026-09-21 planning snapshot had `main`, `origin/main` and `v0.2.0` at `a81de01`, with an uncommitted Add an account prototype. That prototype was preserved as `a776bbe` and the plan on `docs/0.3.0-plan`. The user requested a review pause; work was paused, later explicitly resumed, and committed branches were pushed following the user's push request. Those earlier paused/planning status descriptions are historical, not the current execution state.

The temporary `/private/tmp` implementation checkouts subsequently disappeared. Committed work was recovered from the retained branches into `.worktrees/candidate`; integration and review fixes now live on `feature/0.3.0-candidate`. The original prototype branch remains preserved. This recovery does not claim that missing uncommitted temporary files were recovered byte-for-byte.

## Execution checklist

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
- [ ] Record CI results for the final integrated release-preparation revision.
- [ ] Complete live provider parity/runtime verification, or obtain an explicit alternative-scope decision.

Review: [PR #8](https://github.com/Gfacello/SDF-Account-Status/pull/8), draft. The focused local macOS run passed 28 tests on `0c8bc5c`; this is synthetic evidence.

### 6. Deliver the selected UI features

Implementation and automated coverage are complete for the following items; manual acceptance checks remain in phase 7:

- [x] Refresh reloads accounts, retains valid search/environment/role filters and never writes the project default.
- [x] Environment and role filters combine with text search, retain grouping, show counts/empty states, and restore after refresh/production cancellation.
- [x] Rich status supports persisted display styles, exact-identity metadata, truncation, full tooltips/accessibility and unavailable metadata fallback.
- [x] Open in NetSuite uses validated local account URLs, correct current/selected-row targets and a browser controller without account-switching capability.
- [x] Adapt the preserved Add an account prototype to the Oracle settings/controller flow and cover its controller/picker behavior automatically.
- [ ] Pass Add an account's live end-to-end gate before final release inclusion, or obtain an explicit scope decision.
- [x] Correct reporting wording in README, security/architecture docs and both plugin-description sources: IDE preferences control manual/automatic exception submissions.

Review: [PR #5 refresh/filters](https://github.com/Gfacello/SDF-Account-Status/pull/5), [PR #6 browser actions](https://github.com/Gfacello/SDF-Account-Status/pull/6), [PR #7 rich status](https://github.com/Gfacello/SDF-Account-Status/pull/7), [PR #9 reporting](https://github.com/Gfacello/SDF-Account-Status/pull/9). All are drafts. Add Account integration and cross-feature wiring are on the candidate branch.

### 7. Integrate and verify the candidate

- [x] Integrate the preserved branches in the durable candidate checkout; resolve merge conflicts and reviewed filter/context-click/save/configuration issues.
- [x] Pass an initial combined gate on `83c2695`: 213 tests in 29 suites, zero failures/errors/skips, packaging and configuration/structure checks, and Compatible results for WebStorm `261.22158.274` and `262.8665.259`.
- [x] Inspect that initial ZIP: correct `com.sdf.accountstatus` ID, packaged license and error handler, since-build `261`; artifact version remained `0.2.0`.
- [x] Pass the full gate on current source `8543334` after the provider filesystem-validation threading fix: 215 tests in 29 suites, zero failures/errors/skips, all 20 tasks executed, both WebStorm verifier verdicts Compatible.
- [x] Inspect the fresh `8543334` ZIP: correct ID, version `0.2.0`, since-build `261` with no upper bound, expected library JAR, packaged LICENSE and Marketplace exception handler; checksum recorded in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).
- [ ] Inspect the eventual `0.3.0` release-preparation artifact after version/notes changes.
- [ ] Complete the supported-IDE manual matrix in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md), including actual widget lifecycle/multiple projects and provider changes during a load.
- [ ] Record live provider/setup observations, sanitized configuration and any remaining limitations.
- [ ] Complete required PR reviews before integration into `main`; draft PR creation alone does not satisfy review requirements.

Required gate: `./gradlew clean test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin`.

### 8. Prepare the release review

Branch: `chore/release-0.3.0` (not yet prepared).

- [ ] Set version `0.3.0`, finalize dated changelog/Marketplace notes, and describe only delivered/verified behavior.
- [x] Align candidate provider prerequisites, local configuration, setup instructions and privacy documentation with the implementation; live compatibility limits remain explicit.
- [ ] Run required final gates on the release-preparation revision and create its reviewable PR with evidence.
- [ ] Prepare the post-merge checklist: final-main CI, clean checkout, signed ZIP, checksum, exact tag target and publication steps still required.
- [ ] Present the candidate and remaining release actions for review without merging the release-preparation PR, pushing a release tag or publishing.

## Progress tracking

Check an item only when its observable result is verified. Keep implementation, synthetic tests, manual IDE behavior and live account compatibility distinct. Preserve the selected scope until the user makes an explicit scope decision. The validation record is the durable location for revision-specific results and still-unchecked scenarios.
