# 0.3.0 release review

Prepared 2026-10-05. This is an unpublished candidate. Manual IDE and live NetSuite gates remain open in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

## Review branches

- `feature/0.3.0-candidate`: integrated implementation, [PR #10](https://github.com/Gfacello/SDF-Account-Status/pull/10), targeting `main`.
- `chore/release-0.3.0`: dependent version/Marketplace description/changelog/release-documentation changes. Its draft PR targets the candidate until implementation review is complete; retarget to `main` once the candidate is approved and integrated.
- Focused draft PRs #4–#9 provide component-level review. Avoid merging overlapping changes twice; use them to resolve feedback before selecting the final integration path.

The release branch sets artifact version `0.3.0`; it does not establish readiness to publish. Release notes describe the implemented candidate scope. If the live provider/setup gates fail, obtain the explicit scope decision required by the goal and revise the code and notes together.

## Before approving the release-preparation PR

- [ ] Complete the manual matrix for WebStorm 2026.1 and 2026.2.
- [ ] Demonstrate dedicated-account Node/legacy/Oracle identity and role parity, Add Account completion/cancellation, and unchanged project defaults.
- [ ] Resolve review findings; preserve all selected features unless a scope change is explicitly approved.
- [ ] Record green local and remote validation for the final preparation revision and inspect its versioned ZIP.
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
