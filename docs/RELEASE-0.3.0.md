# 0.3.0 release review

Prepared 2026-10-05. This is an unpublished candidate. Manual IDE and live NetSuite gates remain open in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

## Review branches

- `feature/0.3.0-candidate`: integrated implementation, [PR #10](https://github.com/Gfacello/SDF-Account-Status/pull/10), targeting `main`.
- `chore/release-0.3.0`: dependent version/Marketplace description/changelog/release-documentation changes, draft [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), targeting `feature/0.3.0-candidate`. Retarget to `main` once the candidate is approved and integrated.
- Focused draft PRs #4–#9 provide component-level review. Avoid merging overlapping changes twice; use them to resolve feedback before selecting the final integration path.

The release branch sets artifact version `0.3.0`; it does not establish readiness to publish. Release notes describe the implemented candidate scope. If the live provider/setup gates fail, obtain the explicit scope decision required by the goal and revise the code and notes together.

## Recorded validation

Release source `c280bdd` passed the local full gate and 0.3.0 ZIP inspection; latest observed release head `ca819ac` adds documentation only. The inspected source/artifact checksum and test counts are in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

Integrated candidate [PR #10 CI run 37353758331](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37353758331) passed all three jobs for head `6787477`: Ubuntu full verification plus macOS/Windows synthetic CLI tests. The PR workflow artifact uses merge revision `65537f21db98553b9cb2c7ad88d1f44801effdea`, not the head SHA. The latest observed release [PR #11 run 37356754447](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37356754447) passed all three jobs for head `ca819ac`, with artifact merge revision `ff77d6e8b9adb74465ac92ea99d980a747fa9bc6`. The PR description has been updated with this CI evidence and the verified 261 interactions. Documentation updates require fresh CI observation before approval. Manual/live gates and review completion remain open.

Native WebStorm 2026.1 demonstrated specific synthetic search/filter, refresh retention, missing-Oracle guidance and production Cancel/Confirm interactions, including disk-default preservation/update checks. Native WebStorm 2026.2 demonstrated plugin load and sandbox status/metadata only. Input remained unreliable; licensing readiness and the complete manual matrix remain unverified. See the partial observations in the validation record.

## Before approving the release-preparation PR

- [ ] Complete the manual matrix for WebStorm 2026.1 and 2026.2.
- [ ] Demonstrate dedicated-account Node/legacy/Oracle identity and role parity, Add Account completion/cancellation, and unchanged project defaults.
- [ ] Resolve review findings; preserve all selected features unless a scope change is explicitly approved.
- [x] Record the local `c280bdd` gate/ZIP and passing remote PR #11 checks for head `ca819ac`.
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
