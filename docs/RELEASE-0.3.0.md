# 0.3.0 release review

Prepared 2026-10-05. This is an unpublished candidate. Manual IDE and live NetSuite gates remain open in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

## Review branches

- `feature/0.3.0-candidate`: integrated implementation, [PR #10](https://github.com/Gfacello/SDF-Account-Status/pull/10), targeting `main`.
- `chore/release-0.3.0`: dependent version/Marketplace description/changelog/release-documentation changes, draft [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), targeting `feature/0.3.0-candidate`. Retarget to `main` once the candidate is approved and integrated.
- Focused draft PRs #4–#9 provide component-level review. Avoid merging overlapping changes twice; use them to resolve feedback before selecting the final integration path.

The release branch sets artifact version `0.3.0`; it does not establish readiness to publish. Release notes describe the implemented candidate scope. If the live provider/setup gates fail, obtain the explicit scope decision required by the goal and revise the code and notes together.

## Recorded validation

Release source `c280bdd` passed the local full gate and 0.3.0 ZIP inspection; release head `bdc9c00` adds documentation only. The inspected source/artifact checksum and test counts are in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

Integrated candidate [PR #10 CI run 37353758331](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37353758331) passed all three jobs for head `6787477`: Ubuntu full verification plus macOS/Windows synthetic CLI tests. The PR workflow artifact uses merge revision `65537f21db98553b9cb2c7ad88d1f44801effdea`, not the head SHA. Release [PR #11 run 37355038442](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37355038442) also passed all three jobs for head `bdc9c00` (Ubuntu 3m 52s; total 5m 0s), with artifact merge revision `b40d0f80a4a62c672ab9dab2ad8107a80e846e1f`. These results do not establish the next documentation commit's CI result. Manual/live gates and review completion remain open.

Native WebStorm 2026.1 displayed the 0.3.0 plugin's synthetic sandbox status and account metadata. Picker interaction, licensing readiness and the complete manual matrix remain unverified; see the partial observations in the validation record.

## Before approving the release-preparation PR

- [ ] Complete the manual matrix for WebStorm 2026.1 and 2026.2.
- [ ] Demonstrate dedicated-account Node/legacy/Oracle identity and role parity, Add Account completion/cancellation, and unchanged project defaults.
- [ ] Resolve review findings; preserve all selected features unless a scope change is explicitly approved.
- [x] Record the local `c280bdd` gate/ZIP and passing remote PR #11 checks for head `bdc9c00`.
- [ ] Observe final CI after subsequent documentation changes; inspect/validate any newly changed artifact before approval.
- [ ] Replace the candidate changelog heading with the intended release date after the release is approved.

## After approval and merge — separate release actions

1. Record the exact final `main` SHA and require successful CI on that SHA.
2. Use a clean isolated checkout of that SHA. Repeat the full gate in [RELEASING.md](RELEASING.md), inspect the ZIP, and verify the installed final artifact.
3. Supply signing material through the protected environment. Run `signPlugin verifyPluginSignature`; do not store secrets in the repository or logs.
4. Record the signed ZIP filename and SHA-256, and retain those exact bytes for both destinations.
5. With release authorization, create the immutable signed `v0.3.0` tag at the recorded final SHA. If tag signing is unavailable, obtain and record the accepted policy before tagging.
6. With publication authorization, publish that verified signed ZIP to JetBrains Marketplace and the matching GitHub release. Confirm notes, vendor, compatibility, and requirements match the validated scope.
7. Install the Marketplace build in a clean supported IDE and complete the post-publication checks in the release guide.

No release tag, release merge, signing operation, or Marketplace/GitHub publication has been performed as part of this preparation.
