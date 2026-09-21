# Goal: prepare NetSuite SDF Account Status 0.3.0 for release

Status: active. Resumed on 2026-09-21 after goal activation. Release publication and release-tag creation remain outside this preparation goal.

## Objective and completion

Prepare a reviewable 0.3.0 release candidate following the acceptance criteria in [PLAN-0.3.0.md](PLAN-0.3.0.md). Include Refresh, Richer status bar, Open in NetSuite, Environment and role filters, all four selected refactors, their regression tests, and the error-reporting documentation correction.

Resolve the Node.js CLI migration and Add an account feasibility questions early. Implement the supported provider/setup approach when demonstrated; otherwise record the unresolved constraint and obtain a scope decision before claiming completion.

The goal is complete when the agreed implementation, documentation, passing validation evidence, and reviewable release-preparation PR are ready. Publishing, pushing a release tag, and merging the release-preparation PR are later release actions requiring authorization. Those later actions are documented in [RELEASING.md](RELEASING.md).

## Git strategy

- `main` holds integrated, tested work for the next release. Changes enter through reviewed PRs; published versions are identified by immutable tags.
- Use focused branches from the latest integrated `main`: `research/...`, `refactor/...`, `feature/...`, and `docs/...`.
- Use `chore/release-0.3.0` for the final version, changelog, Marketplace notes, and release documentation PR.
- Tag the final approved, validated `main` commit as `v0.3.0` when releasing. The tag is not created during feature development.
- A separate `release/0.3.0` stabilization branch is only needed if work for later versions must proceed before 0.3.0 ships. If introduced, add push CI coverage for it and carry fixes back to `main`.
- Keep each PR focused, with its behavior tests and relevant documentation. Respect repository review requirements before merging; use dependent PRs or isolated worktrees when prerequisites are still under review.
- Preserve other ongoing work. Do not reset the shared checkout, overwrite existing changes, rewrite shared history, or move existing release tags.

This is a lightweight branch-and-PR workflow consistent with [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow).

## Starting state

Observed locally on 2026-09-21: `main`, its local `origin/main` tracking ref, `feature/add-account`, and `v0.2.0` point to `a81de01`. The worktree contains uncommitted Add an account code and documentation, plus release-planning documents. Remote refs must be refreshed before execution; this observation is not a live remote-state guarantee.

The existing CI runs on pull requests and pushes to `main`/`master`. It does not currently run on `release/*` branch pushes or tag pushes. Uploaded artifacts can exist even after a failed job because artifact upload uses `if: always()`; require a successful validation job for the relevant commit.

## Execution checklist

### 1. Preserve work and establish the baseline

- [x] Recheck status and branches, identify any changes made since planning, and preserve all current work.
- [x] Move/checkpoint the Add an account prototype on the existing `feature/add-account` branch if its state still permits a safe switch. Review and stage only the intended files/hunks, keeping planning changes separate. Preserved as local commit `a776bbe`; no runtime validation is implied by this checkpoint.
- [x] Prepare the planning/release-workflow documentation on `docs/0.3.0-plan`.
- [ ] Refresh remote refs after the worktree is safely checkpointed; create subsequent branches from the intended reviewed base.
- [ ] Run the existing tests and record the actual result, distinguishing fresh execution from cached/up-to-date results.

Exit: the prototype and planning work are preserved on appropriate branches, the implementation base is known, and baseline verification is recorded.

### 2. Resolve the supported account-provider approach

Branch: `research/node-cli-compatibility`.

- [ ] Verify Node.js CLI `account:manageauth --list` and any needed `--info` metadata with synthetic or dedicated test accounts; record sanitized fixtures and tested versions.
- [ ] Verify account identity/role visibility across the current CLI, Node.js CLI, and Oracle WebStorm settings under matching execution contexts.
- [ ] Determine the supported runtime and launcher approach and whether a transitional Java provider is necessary.
- [ ] Demonstrate the proposed Add an account path without changing the project default; document cancellation and missing-plugin behavior.
- [ ] Record the chosen provider, setup flow, fallback policy, and any user-required compatibility decisions. Keep the saved-account-URL design unless a supported UI URL source is demonstrated.

Exit: enough evidence exists to select the integration approach. Do not silently omit migration or Add an account when evidence is unavailable; record the open decision.

### 3. Refactor account workflows with regression tests

Branch: `refactor/account-workflows`.

- [ ] Extract workflow control from the widget with small boundaries for account loading, confirmation, and document updates.
- [ ] Introduce the provider boundary selected in step 2 without changing observable account-selection behavior.
- [ ] Test production Confirm/Cancel, selecting the current account, preservation of unrelated document edits, read-only files, failed saves, and state updates after persistence.
- [ ] Replace the misleading cancellation setter test with coverage of the actual cancel/reopen flow.

Exit: workflow behavior is testable independently of popup construction, and focused regression tests pass.

### 4. Refactor state and presentation

Branch: `refactor/account-state`.

- [ ] Read and parse one project snapshot per refresh; derive the label and picker current-account marker from that snapshot.
- [ ] Publish account-load state together after checking the request generation on the UI thread.
- [ ] Use one shared status-presentation function for initial loading, switching, refresh, and external edits.
- [ ] Test out-of-order completion, cancellation, project disposal, external edits, and consistent status presentation with controllable scheduling.

Exit: all four selected refactors are delivered and covered by meaningful tests.

### 5. Implement and validate the provider transition

Branch: `feature/node-cli-provider`, if supported by the step 2 decision.

- [ ] Implement provider-specific discovery/parsing behind the common account result.
- [ ] Verify empty lists, malformed output, missing tooling, timeouts, cancellation, same-account/different-role identities, and chosen fallback behavior.
- [ ] Keep direct `project.json` status display available when CLI account discovery is unavailable.
- [ ] Update CLI dependencies, command scope, privacy, and migration documentation to describe the implementation actually delivered.

Exit: the supported provider passes its contract tests and compatibility checks, or the user has explicitly accepted a documented alternative release scope.

### 6. Deliver the selected UI features

Use focused branches and the detailed acceptance criteria in [PLAN-0.3.0.md](PLAN-0.3.0.md):

- [ ] `feature/account-refresh`: reload while preserving search/filter state and the project default.
- [ ] `feature/account-filters`: combine environment, role, and text search, including empty results and restoration after refresh/cancellation.
- [ ] `feature/rich-status-bar`: optional detailed display, persisted preference, tooltips, truncation, and unavailable metadata behavior.
- [ ] `feature/open-in-netsuite`: local account URL configuration, correct current/selected-row targeting, browser launch, and no account switch.
- [ ] `feature/add-account`: adapt the preserved prototype to the chosen provider/setup flow and pass its end-to-end gate before inclusion.
- [ ] Correct error-reporting wording across the README, security/architecture documentation, and both plugin-description sources.

Exit: selected features and their integration tests pass. Other suggested features and optional performance projects remain outside scope.

### 7. Integrate and verify the candidate

- [ ] Resolve review feedback and integration conflicts without discarding unrelated work. Follow review requirements for feature/refactor PRs targeting `main`.
- [ ] Run the full gate: `./gradlew clean test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin`.
- [ ] Inspect the ZIP and verify the descriptor, expected files, license, and WebStorm 2026.1/2026.2 compatibility reports.
- [ ] Smoke-test the installed candidate in supported WebStorm versions: keyboard operation, accessibility, light/dark themes, narrow layouts, persistence, production confirmation, setup, refresh/filter interaction, and browser opening.
- [ ] Record the commit, environment, test results, manual observations, and any unresolved limitations. Unperformed manual checks remain unchecked.

Exit: one identifiable candidate satisfies the agreed acceptance criteria and validation gates.

### 8. Prepare the release review

Branch: `chore/release-0.3.0`.

- [ ] Set version `0.3.0`, finalize the dated changelog and Marketplace notes, and describe only delivered/verified behavior.
- [ ] Align supported CLI prerequisites and setup instructions with the provider decision; remove unshipped prototype claims if scope was explicitly changed.
- [ ] Run relevant final gates on the release-preparation commit and create/update its reviewable PR with validation evidence.
- [ ] Prepare the post-merge checklist identifying the final-main CI run, clean checkout, signed ZIP, checksum, exact tag target, and publication steps still required.
- [ ] Present the release candidate and remaining release actions for review; do not publish or push the release tag as part of this preparation goal.

Exit: the goal's reviewable implementation and release-preparation handoff are complete.

## Progress tracking

On resumption, change this document's status to active. Check items only after their observable result is verified, and record branch/PR URLs, commit IDs, and validation evidence beside the completed phase. Resolve scope questions at their decision points rather than treating an investigation as an implemented feature.
