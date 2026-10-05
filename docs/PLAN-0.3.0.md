# NetSuite SDF Account Status 0.3.0 — scope and acceptance criteria

Status: implementation integrated for review on `feature/0.3.0-candidate`, source revision `8543334` as of 2026-10-05. Version metadata remains `0.2.0` pending release preparation. Live compatibility, manual IDE checks, integrated release-preparation CI and the release-preparation PR remain outstanding; no release has been published.

Execution order, branch strategy, completion criteria and progress tracking are in [GOAL-0.3.0.md](GOAL-0.3.0.md); revision-specific evidence and the manual matrix are in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md). The goal is active. The initial draft and review-pause descriptions are historical; this update records implementation progress without changing the selected scope or acceptance criteria.

Goal: make the active account easier to recognize, filter and refresh accounts from the picker, and open an account in NetSuite from the IDE. Include the selected refactors, validate the complete account-setup flow, and investigate migration from the retired Java CLI before release.

## Release scope

| Priority | Item | Current status |
| --- | --- | --- |
| Included, live gate pending | Add an account button | Oracle settings/controller flow integrated with automated coverage; live setup/cancel/default preservation and provider visibility still required |
| Must have | Refresh button in the picker | Implemented with automated filter/default/loading regression coverage; manual IDE checks pending |
| Must have | Richer status bar | Implemented with persisted style, rich metadata, tooltip/accessibility and fallback tests; manual IDE checks pending |
| Must have | Open in NetSuite | Implemented with validated local account URLs and current/row target tests; real dialog/browser checks pending |
| Must have | Environment and role filters | Implemented with combined filtering, counts and refresh/cancel restoration tests; manual interaction checks pending |
| Included | All focused refactors | Workflow controller/gateway, one project snapshot, coherent EDT publication and shared presentation implemented with regression tests |
| Included, live gate pending | Node.js CLI account discovery | Candidate defaults to Node with explicit legacy Java option and local path settings; live cross-tool parity still required |
| Maintenance | Correct error-reporting documentation | Corrected in user/security/architecture docs and both plugin-description sources; existing reporter retained |

## Add an account

Status: implemented in the integrated candidate, pending live end-to-end validation. The preserved prototype is checkpointed on `feature/add-account`; the candidate uses an `AccountSetupController` and the public IntelliJ settings API to open Oracle's Account Management, then reloads and clears picker filters when settings close. Automated controller/UI tests do not demonstrate the complete flow in real supported IDE/account contexts.

User story: I can start adding a NetSuite account from the picker and see it in the refreshed list after completing setup.

Implemented flow: click **Add an account** beside the search field, use **+** in Oracle's Account Management settings to complete authentication, then close settings to return to the refreshed picker. Select the new account separately to make it the project's default.

Dependency: Oracle's **SuiteCloud IDE Plug-in for WebStorm** must be installed and enabled for this setup flow. Oracle's plugin handles authentication and credential storage. Validate that accounts created there are visible through the account provider selected for the release, including the candidate's default Node.js CLI provider. The user has volunteered a dedicated test setup; authentication ID/details are pending.

Feasibility questions to resolve:

- Can the button reliably open the intended Account Management page with Oracle's plugin installed on each supported WebStorm version?
- Can a user complete account setup there and return to the picker cleanly after closing the settings dialog?
- Does an authentication ID created through that UI appear in the same CLI account list this plugin reads, with the required account and role metadata?
- Does that remain true with the Node.js CLI provider, or is a different supported setup flow required?
- Does the complete setup flow preserve the project's existing default, including when the user cancels or closes settings after authentication?
- Do missing-plugin and unavailable-settings cases provide useful guidance without leaving the picker in a broken state?

Release gate: record observed results and the supported configuration before shipping this feature. If the end-to-end flow cannot be demonstrated, obtain an explicit user scope decision before deferring it or changing release notes.

Acceptance criteria:

- The action is available when the account list is loaded, loading, empty, or unavailable.
- Missing or disabled Oracle SuiteCloud IDE support produces actionable setup guidance.
- Returning from settings reloads accounts and clears the previous search and filters so they cannot hide a newly added account.
- Adding an account leaves the project's default unchanged until the user selects an account.
- Selecting a production account continues to require confirmation.

## Refresh accounts

User story: after accounts are added, removed, or updated through SuiteCloud tooling, I can refresh the open picker to see the latest list.

Implemented placement: a **Refresh** button with the IDE refresh icon to the right of the search field, beside **Add an account**. Tooltip and accessible name: **Refresh SuiteCloud accounts**. Refresh can ship independently of Add an account. If the user explicitly chooses to defer account setup, omit its UI from the final release scope.

Acceptance criteria:

- Refresh starts a fresh read of the configured account list and keeps the picker open.
- The search text and valid environment/role filters are preserved and reapplied to the new results. If nothing matches, the picker shows its normal no-results state.
- The project default remains unchanged; refreshing never writes `project.json` or switches accounts.
- Loading is visible and runs without blocking the IDE. Refresh and account activation are disabled while the read is in progress.
- The action is enabled again after success or failure and remains available for empty and error states. The existing Retry action uses the same reload behavior.
- Successful refresh updates account rows, current-account markers, recommendations, and environment metadata used by the status bar.
- If the configured default was removed, the picker explains that it is missing and preserves the value in `project.json`.
- Failures show the existing safe error message and allow another attempt. Previously loaded rows are not presented as freshly verified results.
- Closing the picker or project during loading is handled safely; a superseded request cannot overwrite newer results.
- The button works with the keyboard and screen readers and fits the supported minimum picker size in light and dark themes.

## Richer status bar

User story: I can recognize the current customer, environment, and role without opening the account picker or remembering an authentication ID.

Implemented display styles, selectable from the widget's context menu:

| Style | Example | Behavior |
| --- | --- | --- |
| Authentication ID | `example-sandbox-dev` | Existing compact display; retained as the default |
| Account details | `Example Company · SB1 · Developer` | Account name, environment, and role from the loaded account metadata |

Acceptance criteria:

- The display preference is remembered locally across IDE restarts.
- The detailed style uses the metadata for the exact current authentication ID, including its role when several authentication IDs point to the same account.
- Environment text distinguishes `PROD`, numbered sandboxes such as `SB1`, `RP`, and `Unverified`, using authoritative account metadata. Existing environment colors and the production icon remain.
- The tooltip exposes the full account name, account ID, authentication ID, environment, and role when available, in either display style.
- Long company names and roles are shortened to fit the status bar while keeping the environment visible; the tooltip and accessible description retain the complete information.
- While metadata is unavailable, display the authentication ID with an unverified state. Existing missing-file, invalid-JSON, and unset-default messages remain actionable.
- Successful account switching, Refresh, and external changes to `project.json` update the label and tooltip. Metadata from a different authentication ID must never remain attached to the new default.
- Normal click and Enter/Space activation continue to open the account picker; the new context menu is keyboard accessible.

## Open in NetSuite

User story: I can open the current or highlighted account in my browser directly from the IDE.

Implemented entry points: **Open in NetSuite** in the status widget's context menu for the current account, and in a picker account row's context menu for that row. The picker menu is also keyboard accessible and names the target account clearly.

URL source for the initial version: a NetSuite UI URL copied by the user from **Setup > Company > Company Information > Company URLs**, saved locally per account ID. On first use, offer **Set account URL**; allow editing it later. Authentication IDs for different roles on the same account share the URL; production and each sandbox retain separate URLs.

Oracle explicitly advises using the account's supplied URL rather than constructing a hostname from the account ID. The Node response domain has not been demonstrated to be a canonical UI URL, so automatic URL discovery remains outside the current implementation. See [Oracle's account-specific domain documentation](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1498251763.html).

Acceptance criteria:

- Open the configured HTTPS NetSuite UI URL in the user's default browser only when the action is invoked.
- Opening an account leaves the project's default and `project.json` unchanged, including when a different picker row is used.
- The first-use setup identifies the account being configured, validates the NetSuite UI URL, and stores the account URL locally without credentials or session tokens.
- If no authoritative account ID is available, explain that the account list must be loaded before an account URL can be configured or opened.
- Missing or invalid URLs offer setup or correction; a browser-launch failure produces an actionable message.
- The browser uses its existing NetSuite session or normal sign-in flow. The action does not select the CLI authentication role in the browser or promise automatic login.
- Tests distinguish current-account and selected-row targets, production and sandbox URL mappings, and accounts with multiple authentication roles.

## Error-reporting documentation

README, SECURITY, architecture notes and both plugin-description sources now explain that reports may be submitted manually or automatically according to the user's IDE error-reporting settings. This remains distinct from the plugin's own analytics behavior.

The Marketplace error handler is already registered and packaged in 0.2.0. This is a documentation correction, not a new 0.3.0 reporting feature. See [JetBrains' error-handler documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html).

## Validation and release preparation

- Cover refresh success, failure and retry, preserved search, removed current account, and prevention of duplicate loads with focused tests.
- Cover rich-label formatting, metadata fallback, preference persistence, account URL validation, and browser action target selection with focused checks.
- Resolve the Add an account feasibility questions and verify its acceptance criteria before release.
- Manually check account setup and its return-to-picker flow, refresh, both status display styles, URL setup and browser opening, keyboard operation, loading states, and narrow layouts on the supported WebStorm versions.
- Run the existing release checks in [RELEASING.md](RELEASING.md) once implementation is complete.
- At release preparation, set version 0.3.0 and align the changelog and Marketplace change notes with delivered behavior.

## Environment and role filters

User story: I can narrow the configured accounts by environment and role without having to type those values into every search.

Implemented controls: an environment selector with **All environments**, **Sandbox**, **Production**, **Release Preview**, and **Unverified**; a role selector with **All roles** and the roles present in the complete loaded account list. Both combine with the existing search field.

Acceptance criteria:

- Text search, environment, and role constraints combine using AND. Each selector defaults to All and can be reset independently.
- Environment filtering uses authoritative account metadata; an authentication ID containing words such as sandbox or production must not determine its environment.
- Role choices are deduplicated and remain available regardless of the current search or environment filter.
- Preserve current/recommended grouping and ordering among matching accounts, hiding groups with no matching accounts.
- Show the matching and total account counts and a clear no-results state with an action to clear search and filters.
- Refresh and cancelled production confirmation preserve search and valid filter selections. If a selected role no longer exists after refresh, clear that role selection with a brief explanation.
- Filtering never changes the project's default or writes `project.json`. Production confirmation still applies when activating a result.
- Controls are keyboard accessible and fit the supported minimum picker size with Add an account and Refresh.
- Test combined constraints, same-account/different-role entries, unknown environments, zero results, clearing, and filter restoration after refresh/cancellation.

Other proposed new features were not selected. The scope remains the features listed in the release table.

## SuiteCloud CLI migration investigation

Verified on 2026-09-21: Oracle has ended support for **SuiteCloud CLI for Java**, whose final version is **2025.2**; downloads remain available until NetSuite **2027.1**. Oracle directs users toward the Node.js CLI and supported IDE integrations. The announced deadline concerns download availability and does not specify a date when installed Java CLI copies stop executing. See [Oracle's Java CLI lifecycle notice](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1489072226.html).

The preserved legacy adapter uses `sdfcli manageauth -list` or the SDK Java CLI JAR. The integrated candidate now defaults to Node, with **Account provider: … > Configure account provider…** exposing explicit legacy selection and optional absolute Node/JavaScript entrypoint paths. Settings are local and applying a change reloads open widgets; failures never silently select another provider. Direct `project.json` status display remains independent of either CLI.

Implemented candidate approach: retain Kotlin/WebStorm and add a provider for Oracle's **SuiteCloud CLI for Node.js**, subject to the unchanged live compatibility gate. Its documented read-only commands include:

```text
suitecloud account:manageauth --list
suitecloud account:manageauth --info <authId>
```

`--list` supplies configured authentication IDs; `--info` supplies account/role/domain information. Public formatter/source inspection and synthetic fixtures informed the parser; real supported-version output and cross-tool metadata parity still require validation. See [Oracle's account-management command reference](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_157304934116.html) and [current SDK release notes](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1558730192.html).

Migration requirements and remaining live gate (see [recorded evidence](NODE-CLI-COMPATIBILITY.md)):

1. Introduce a small account-provider boundary returning normalized authentication ID, account ID, account name, role, and environment metadata. Keep provider-specific process arguments and parsing outside the UI/controller.
2. Exercise Node.js account listing and any required detail lookups using synthetic/dedicated test accounts. Verify runtimes, launchers, output formats, timeouts, cancellation, and supported operating systems. Record the tested CLI version and its runtime requirements.
3. Verify whether authentication IDs from the current Java CLI and Oracle's WebStorm Account Management are visible with identical account/role meanings under the same OS user and execution context. Browser-based and machine-to-machine authentication use different stores, and Oracle states that authentication IDs are not accessible across those contexts. Do not merge accounts from different providers merely because authentication IDs match. See [Oracle's execution-context documentation](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/article_0113125121.html).
4. The candidate implements Node.js as the default, but release inclusion still requires demonstrated compatibility or an explicit scope decision. Retain Java only as an explicit transitional option; expose the selected provider and keep the status display usable when account listing is unavailable. Minimal provider/path configuration may be required; a separate CLI setup assistant is outside selected scope.
5. Use documented commands without inspecting credential files or calling private Oracle plugin classes. Keep process output out of logs and error messages.
6. Revalidate Add an account with the chosen provider before release. Oracle's documented `suitecloud account:setup` is interactive and sets the project's default authentication ID, so it cannot be substituted blindly for an add-only flow that promises to preserve the current default. See [Oracle's account-setup documentation](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/article_89132630266.html).
7. Reassess Open in NetSuite metadata only if the provider supplies a verified account-specific UI URL. A domain value such as `netsuite.com` is insufficient to replace the saved-account-URL design.

Exit criteria remain: demonstrate metadata parity and account identity compatibility, document the live supported setup path, and confirm the implemented transition/fallback policy before describing migration as verified in final release notes.

The Node.js CLI still requires a supported JDK as well as Node.js. Record requirements for the chosen CLI version rather than assuming that this migration removes the Java runtime dependency. See [Oracle's installation prerequisites](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_1558708810.html).

## Refactoring scope and validation

Historical initial audit on 2026-09-21: 79 tests across nine classes were initially observed from an up-to-date run; a later fresh baseline run and complete baseline gate passed. On 2026-10-05 the integrated candidate at `83c2695` passed 213 tests in 29 suites, packaging/structure checks and both configured WebStorm verifiers. The subsequent source fix at `8543334` passed its own full gate: 215 tests in 29 suites, zero failures/errors/skips, all 20 tasks executed and both WebStorm verifiers Compatible. Focused Windows/macOS synthetic CLI jobs and Ubuntu full verification also passed on `0c8bc5c` in PR #8; those remote results apply to that branch revision. See [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md). No runtime performance benchmark is claimed.

### Better tests

The integrated automated suite now covers strict JSON, text preservation, parsers, error redaction, grouping, recommendations and the following workflow boundaries. These remain acceptance requirements; manual/live items are not satisfied by synthetic tests:

1. **Account switching and persistence:** use real IntelliJ documents to exercise read-only files, unrelated unsaved edits, successful saves, save failures, and rollback. Assert both document/disk outcomes and the displayed account.
2. **Production confirmation:** drive the actual Cancel and Confirm flows; verify zero writes on Cancel or selection of the current account, one update on Confirm, and restored search after cancellation. The former setter-only cancellation test has been replaced with actual cancellation/reopen coverage for all filters.
3. **Refresh ordering and disposal:** use controllable tasks to finish older/newer requests out of order, close the picker or project during a load, and verify that stale results cannot replace current state. Avoid timing tests based on sleeps.
4. **Picker interactions:** exercise Enter on groups versus accounts, double-click, loading/error/retry transitions, and Add Account callbacks on the Swing event thread. Keep layout assertions only for intentional UI requirements.
5. **Actual process execution:** use a synthetic CLI executable to test timeouts, cancellation, output capture, and paths containing spaces. Synthetic subprocess fixtures now cover these behaviors. Focused Windows/macOS CI jobs and Ubuntu full verification passed on `0c8bc5c` in PR #8; real Oracle CLI process/authentication checks remain separate.
6. **Selected features:** cover combined environment/role/text filters; rich-label metadata fallback and role changes; URL validation and opening a selected row without switching the default; manually validate Add Account against real supported Oracle plugin versions and the selected account provider before including it.

Use feature-level tests with real platform components for IDE behavior, following [JetBrains' testing guidance](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html). Preserve the fast pure-core tests.

### Included refactors

All four refactors below are selected for 0.3.0 and implemented with regression coverage. Their original motivation and acceptance requirements are retained here.

- **Extract account workflow control from the widget.** Isolate loading, confirmation, and document updates behind small injectable boundaries so the critical workflows above can be tested. The widget should primarily connect UI events and render state.
- **Read one project snapshot per refresh.** The original `refreshAsync` read/parsed `project.json` through the resolver and again for the authentication ID. Derive the label and picker marker from one parsed result so an external edit between reads cannot make them disagree. Retain change/version checks for edits during a refresh.
- **Publish account-load results together.** Build the result in the background, then recheck its generation and publish its state on the UI thread. The original worker checked generation before separately assigning shared metadata and list state; the new EDT publication and regression tests cover that interleaving.
- **Share status formatting.** Initial resolution and post-selection updates previously duplicated tone, icon and tooltip construction. Use one presentation function before adding the richer display style.

### Deferred optimization investigations

These remain optional investigations and are not part of the selected refactoring scope.

- Defer CLI startup for projects without `project.json`; currently every widget with a project base path starts an account load and a two-second file poll. Preserve detection when a project file is subsequently created.
- Skip identical picker-model rebuilds and preserve navigation for changed models. An unrelated `project.json` edit currently triggers a tree rebuild and selection reset.
- Measure picker filtering/rendering with 100, 1,000, and 5,000 synthetic accounts before introducing search debounce or background model building.
- Measure CLI launches with multiple open projects before introducing a shared in-flight account-list request. Any shared cache must respect SDK configuration and explicit refresh.
- Investigate local build startup: the offline test command still attempted a JetBrains release-list request, timed out, and fell back to cached metadata. Keep local tests from paying that network delay where the Gradle plugin supports it.

The original implementation order has progressed to integration/release validation. Next: complete manual IDE checks and live Node/Oracle/Add Account validation, then prepare `chore/release-0.3.0` and its final local/remote checks. Keep unselected feature ideas and optional optimization projects deferred.

## Proposed release notes

These remain a draft for release preparation. Live setup/default-preservation and provider compatibility claims require the open gates or an explicit scope decision before finalization. Focused draft reviews are [#3](https://github.com/Gfacello/SDF-Account-Status/pull/3), [#4](https://github.com/Gfacello/SDF-Account-Status/pull/4), [#5](https://github.com/Gfacello/SDF-Account-Status/pull/5), [#6](https://github.com/Gfacello/SDF-Account-Status/pull/6), [#7](https://github.com/Gfacello/SDF-Account-Status/pull/7), [#8](https://github.com/Gfacello/SDF-Account-Status/pull/8) and [#9](https://github.com/Gfacello/SDF-Account-Status/pull/9). The release-preparation PR has not yet been created.

### Added

- Start account setup with **Add an account** in the picker, using Oracle's NetSuite Account Management settings.
- Reload the account list after account setup while keeping the project default unchanged until an account is selected.
- Refresh configured accounts without closing the picker or losing the current search.
- Choose an account-details status label showing the customer, environment, and role, with full account information on hover.
- Open the current or selected account in NetSuite using a locally saved account URL.
- Filter configured accounts by environment and role alongside text search.

### Improved

- Consolidate account workflow control, project-state reads, background result publication, and status presentation, with regression tests for account switching and refresh behavior.
- Clarify how IDE preferences control JetBrains Marketplace error reporting.
