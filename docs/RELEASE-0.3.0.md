# 0.3.0 release preparation

Updated 2026-10-07. The user reported that the local test works after the account-loading fix at `caeb04b` and explicitly requested that the fix ship in **0.3.0**, not 0.3.1. The authorized next steps are to push the fix, complete its PR and merge into `main`, then publish the actual GitHub `v0.3.0` release. This supersedes the earlier draft-only handoff and review pause. Final merge, artifact and publication evidence remains to be recorded.

## Accepted fix and validation

The fix shows account-loading progress and reduces discovery delay. The development build containing it was labelled `0.3.1-dev.1`; that was a testing label, not the intended release version. Its local automated gate passed **229 tests across 29 suites**, with zero failures, errors or skips, and Plugin Verifier reported Compatible for WebStorm `261.22158.274` and `262.8665.259`. The log is `/private/tmp/sdf-account-loading-feedback-validation.log`. These results identify the development build; the final 0.3.0 artifact needs its own version, checksum and revision record.

The user's successful local test is acceptance evidence for the loading fix. It does not establish that every manual scenario or live Node/Oracle identity and Add Account check has been exercised. Keep the remaining boxes open in [VALIDATION-0.3.0.md](VALIDATION-0.3.0.md), preserve all selected features, and describe those limits accurately in the release notes.

## Final release checklist

- [ ] Push the loading fix and 0.3.0 release preparation, resolve PR findings and required checks, and merge the fix into `main`.
- [ ] Record the exact final `main` revision and successful CI for that revision.
- [ ] Build the final **0.3.0** ZIP from that revision, complete the automated gate in [RELEASING.md](RELEASING.md), and inspect the packaged plugin descriptor and contents.
- [ ] Record the ZIP filename, SHA-256, source revision and signing status; use those exact bytes for the release asset.
- [ ] Update the early `v0.3.0` tag from `17f8cc5` to the verified final merged revision under the user's instruction to include this fix in the still-unpublished 0.3.0 release. Record both targets and verify the remote tag. This is an explicit correction of the prepublication tag; subsequent published tags remain immutable.
- [ ] Publish the GitHub release for that final `v0.3.0` target, replacing the prior draft/testing artifact with the verified 0.3.0 ZIP and accurate release notes.
- [ ] Verify the published release state, tag target and downloadable asset checksum, then provide the release link and installation ZIP to the user.

These are pending actions, not claims that the fix has already merged, the tag has moved or the release has been published. Follow [RELEASING.md](RELEASING.md) for signing and publication requirements. No Marketplace publication is claimed here.

## Remaining validation coverage

- [ ] Complete the remaining WebStorm 2026.1 and 2026.2 manual matrix; prior observed checks remain scoped to their recorded builds.
- [ ] Demonstrate dedicated-account Node/legacy/Oracle identity and role parity, Add Account completion/cancellation, and unchanged project defaults.
- [ ] Record further test results against the final 0.3.0 ZIP and resolve any confirmed defects without silently removing features.

Earlier candidate gates, PR review history, checksums and native IDE observations remain in the validation record. They are historical evidence and must not be presented as new runs against the final release revision.
