# 0.3.0 merge, package and local testing

Updated 2026-10-07. The user explicitly requested that the PRs be consolidated and merged into `main`, then a fresh ZIP and `v0.3.0` tag be prepared for their local testing. This order supersedes the earlier review-only pause. Manual IDE and live NetSuite checks remain open in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md); they have not been waived or recorded as passing. Marketplace publication and a public GitHub release remain outside the current authorization.

## Consolidation plan

- `feature/0.3.0-candidate`: integrated implementation, [PR #10](https://github.com/Gfacello/SDF-Account-Status/pull/10), targeting `main`.
- `chore/release-0.3.0`: dependent version/Marketplace description/changelog/release-documentation changes, [PR #11](https://github.com/Gfacello/SDF-Account-Status/pull/11), targeting `feature/0.3.0-candidate`. Merge #11 into the candidate first, then merge #10 into `main`.
- Confirm that PRs #3–#9 are fully included in #10, then close them as superseded. Preserve their review and validation history.

The release branch sets artifact version `0.3.0`; it does not establish readiness to publish. Keep every selected feature in the merged testing build. If subsequent manual or live checks find defects, fix them and update the notes and evidence; do not silently remove the provider or setup flow.

## Recorded validation before consolidation

Release source `c280bdd` passed the local full gate and 0.3.0 ZIP inspection; latest observed release head `3eba7aa` adds documentation only since that source gate. The inspected source/artifact checksum and test counts are in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md).

Integrated candidate [PR #10 CI run 37353758331](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37353758331) passed all three jobs for head `6787477`: Ubuntu full verification plus macOS/Windows synthetic CLI tests. The PR workflow artifact uses merge revision `65537f21db98553b9cb2c7ad88d1f44801effdea`, not the head SHA. The latest observed release [PR #11 run 37360084211](https://github.com/Gfacello/SDF-Account-Status/actions/runs/37360084211) passed all three jobs for head `3eba7aa`, with artifact merge revision `0690daec031e3f7f149214e0751f1bbcc9cca944`. Subsequent documentation updates require fresh CI observation before approval. Remaining manual/live gates and review completion remain open.

Native WebStorm 2026.1 demonstrated specific synthetic search/filter, refresh retention, missing-Oracle guidance and production Cancel/Confirm interactions, including disk-default preservation/update checks. It also showed missing-file and malformed-JSON states, then recovery to the restored production/Developer baseline. A fresh native WebStorm 2026.2 session, with Trust Project accepted only for synthetic `project262`, demonstrated missing-file and malformed-JSON states followed by recovery to the restored valid baseline. External default edits changed the same account's role from Developer to Administrator and back. The install/load and missing/invalid-project recovery row is complete in both IDE versions; the 261 combined-filter/cancel and refresh rows are also complete. The external-edit/exact-role portion of the 262 status row is observed. Remaining compound rows stay open in the validation matrix. Input has been intermittent; raising the main 261 window allowed one picker session, but later picker/context actions again had no visible effect. Licensing readiness is unconfirmed. See the individual observations in the validation record.

## Authorized merge and test-artifact checklist

- [ ] Recheck PR contents, review findings and CI; resolve confirmed defects and required checks.
- [ ] Merge PR #11 into `feature/0.3.0-candidate`, then PR #10 into `main`; close #3–#9 as superseded once their inclusion is verified.
- [ ] Record the exact final `main` SHA and successful CI for that revision.
- [ ] Build from a clean checkout of that SHA, run the complete automated gate in [RELEASING.md](RELEASING.md), and inspect the resulting 0.3.0 ZIP.
- [ ] Record the ZIP filename, SHA-256, source revision and signing status, then provide the exact artifact for local installation.
- [ ] Create and push the immutable `v0.3.0` tag at that same verified `main` revision, following the signing policy in the release guide. Never move an existing release tag.
- [ ] Prepare a draft GitHub release with the same ZIP and accurate notes stating that manual/live testing is pending. Keep it unpublished.
- [ ] Hand the ZIP and remaining checks to the user for testing.

These entries describe the requested next actions. They do not claim that consolidation, the final build, tagging or draft creation has already happened.

## Testing and later publication

- [ ] Complete the remaining WebStorm 2026.1 and 2026.2 manual matrix; the install/load and missing/invalid-project recovery row is already verified in both versions.
- [ ] Demonstrate dedicated-account Node/legacy/Oracle identity and role parity, Add Account completion/cancellation, and unchanged project defaults.
- [ ] Record the user's local test results against the supplied ZIP and preserve any unresolved limitations.
- [ ] Resolve findings and validate any changed artifact before requesting publication authorization. If fixes are required after tagging, use a new version/tag rather than moving `v0.3.0`.

After testing and separate publication authorization, follow [RELEASING.md](RELEASING.md) for protected signing, signature verification, Marketplace submission and the public GitHub release. The final signed artifact and publication checks must be recorded independently of the local testing ZIP. No Marketplace publication is claimed by this document.
