# Account UI and profile settings — 2026-10-14

- AuthActivity remains native Java Views; MyPageActivity retains its separate route.
- White canvas, purple avatar card, list-style account/security actions, blue sign-in
  button, persistent field labels, 48dp+ actions, scrollable narrow-screen layout.
- Name: Firebase Authentication updateProfile; verified users additionally use existing
  CloudStore.saveProfile (same users schema). Unverified users never write a profile.
- Photo: ACTION_OPEN_DOCUMENT + persisted read permission; UID-scoped account_avatars
  local preferences. No photo uploads, billing changes, Storage setup or new dependencies.
  The UI explicitly discloses device-only photos. A missing/revoked URI shows a default avatar.
  Images decode off the main thread with sampling; pending image results use weak View references.
- Draft name/photo survive Activity recreation. Save and cancel are separate; passwords
  and credentials are not stored in Bundles. Existing operation ownership/request gate remains.

## Verification

- assembleDebug / assembleDebugAndroidTest succeeded.
- Existing 15 offline AuthLifecycleTest + AssignmentStorageTest cases passed.
- ProfileUiTest: 3 cases passed on a read-only API29 Android emulator using a LOCAL Firebase
  Authentication emulator: name save + recreation, UID-scoped photo settings + cancel,
  login controls. Repeated at 320dp with font_scale=1.3, all 3 passed.
- Test screenshot capture uses synthetic accounts and View drawing only. Production
  FLAG_SECURE remains enabled. Reviewed normal/narrow captures; final short login title
  avoids an awkward Korean wrap at large font sizes.
- Actual system photo-picker selection/persisted URI grants on a real phone, verified
  user's live Firestore write, Google sign-in and production mail delivery were NOT tested.

## Reproduce profile tests

Start local auth with `npx -y firebase-tools@latest emulators:start --only auth
--project demo-report-calendar --config firebase.qa.json`. On a disposable emulator,
`adb -s <serial> reverse tcp:9099 tcp:9099`, install app/debug and androidTest APKs, run
`am instrument -w -e class com.reportcalendar.app.ProfileUiTest
com.reportcalendar.app.test/androidx.test.runner.AndroidJUnitRunner`.
The test redirects Auth to loopback 9099 and Firestore to closed loopback port 1; no
production account/data is used. Debug network policy permits HTTP only to localhost;
release HTTPS-only policy is unchanged. Never run on a user's normal phone.
