# Public/private assignments

Existing `report_calendar_native/assignments` JSON and all legacy fields remain intact.
Missing visibility defaults to private. Additive metadata: visibility, cloudId, ownerUid.
No migration uploads existing tasks. No notification implementation is added.

Public content goes to `(default)/assignments/{cloudId}` in project-771b5 (Standard,
Seoul). Public means visible to signed-in non-anonymous users, not anonymous web access.
Document fields: id (=document ID), ownerUid, visibility=public, title (1..200), subject
(1..100), period (1..20), detail (0..10000), dueMillis (integer 0..253402300799999),
complete (boolean), updatedAt (server timestamp). No email, tokens or photo URIs.
Photos remain device-only; shared image hosting requires a separate Storage feature.

MainActivity reads `whereEqualTo("visibility", "public")` on explicit cloud import.
It preserves local IDs/photos, skips pending writes, and treats other authors as read-only.
No realtime listener or automatic cross-device deletion of already imported copies.
Public content may have been copied by recipients; unpublishing cannot recall copies.

AssignmentSync owns a durable per-document outbox (assignment_public_outbox) and writes
it atomically with local tasks in the same SharedPreferences commit. Entries are scoped
to the original UID; private tasks without any publication history never get an entry.
Unpublish/deletion entries contain only UID, revision and delete=true, not private text.
Transactions serialize old/new requests, fail offline, validate ownership, and use a
stable document ID for idempotent retry. Activity recreation does not duplicate sends.
The app retries on foreground entry and on the explicit retry button; no background job.
20s delay is a status message, NOT cancellation or proof of failure. Errors retain the
outbox. Login to the publishing account is required to finish a pending deletion.

Users rules retain the email verification requirement on create/update. Assignment
writes require non-anonymous ownership; profile/email verification policy is unchanged.
Account deletion does not cascade to assignments. Unpublish tasks BEFORE account deletion;
a server-owned cleanup trigger is a separate requirement before broad release.

Security review: deny extra fields, private uploads, invalid types/lengths, forged owner,
owner transfer, anonymous access, non-owner mutation and nested paths. Read allows only
public documents (single absent-document reads support transactions). Rules are a prototype
requiring review before broad release. No production user data used for verification.

## Verification (2026-10-07)

- assembleDebug and assembleDebugAndroidTest: successful.
- JUnitCore: 28 tests passed (6 payload policy, 22 existing auth/deletion regression).
- Disposable read-only API29 emulator, wifi/mobile data disabled: 15 tests passed
  (8 assignment storage/draft/outbox tests, 7 auth lifecycle regressions). Fixtures
  restore original calendar preferences. No connected physical-phone data was changed.
- Firebase Rules evaluator: 249 synthetic cases passed, including missing-document
  transaction reads, authenticated public reads, non-owner writes, private/extra-field
  rejection and previous user-profile verification rules. No actual documents changed.
- With user approval, deployed firestore.rules to project-771b5. This also deploys the
  previously requested email_verified condition for users create/update; owner checks,
  strict field validation, read and delete policy are retained.
- Live account public upload/download, network-loss mid-commit, and cross-device
  integration remain untested. App background execution is not guaranteed; reopen and
  use retry to flush pending work. Recipient copies cannot be recalled on unpublish.

I've set up prototype Security Rules to keep the data in Firestore safe. They are
designed to be secure for authenticated public reads, owner-only validated writes,
and denial of private content and unknown fields. However, you should review and
verify them before broadly sharing your app. If you'd like, I can help you harden
these rules.
