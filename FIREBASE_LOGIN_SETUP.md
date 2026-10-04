# 로그인 설정

2026-09-30 설정 반영: Firebase project-771b5에 debug/local release의 SHA-1 및 SHA-256
4개 등록을 재조회해 확인했습니다. 최신 app/google-services.json을 적용했고 웹 OAuth
클라이언트 및 Android OAuth 클라이언트 2개가 포함된 것을 확인했습니다.
실제 사용자 계정으로 로그인하는 과정은 별도 확인이 필요합니다.

앱 메뉴 → 로그인 · 내 계정에서 이메일/비밀번호 로그인, 회원가입, 비밀번호 재설정,
Google 로그인, 로그아웃을 이용합니다. 이메일 링크 방식은 구현하지 않았습니다.

## Firebase 콘솔

1. https://console.firebase.google.com/project/project-771b5/authentication/providers
   에서 이메일/비밀번호와 Google 제공자를 활성화합니다.
2. Google 제공자 설정의 지원 이메일을 프로젝트 운영자의 이메일로 지정합니다.
3. 프로젝트 설정 → 내 앱 → com.reportcalendar.app에 서명 인증서 SHA-1과 SHA-256을 등록합니다.
   Android Studio 터미널에서 `./gradlew.bat signingReport`로 확인합니다.
   개발용 debug 지문과 배포용 지문은 서로 다릅니다.
   Play 배포 시에는 Play Console의 앱 서명 인증서 지문도 등록합니다.
4. 업데이트된 google-services.json을 다운로드해서 `app/google-services.json`에 넣습니다.
   웹 OAuth 클라이언트(client_type 3)가 있어야 default_web_client_id가 생성됩니다.
5. Gradle Sync 및 `./gradlew.bat assembleDebug` 후 새 APK를 설치합니다.

## 데이터 동작

2026-09-30 로컬 signingReport에서 확인한 인증서 지문:

| 빌드 | SHA-1 | SHA-256 |
| --- | --- | --- |
| debug | D9:8E:C9:DB:50:DE:F2:49:7E:C0:B2:92:8B:6E:2C:63:46:29:A0:BA | 8E:A4:07:AE:DB:D0:98:E6:B2:A5:22:EB:B5:66:38:DE:FE:11:B1:7F:AB:32:74:E1:4F:B8:5C:25:E0:04:2B:C7 |
| local release | B9:EF:BF:E9:23:D9:55:48:1F:4E:B4:7F:B8:D8:D9:86:E0:05:3E:CB | 9D:87:07:9F:D7:11:F0:20:8C:4D:28:EB:74:48:58:1D:BF:B7:61:95:D3:75:B9:5C:7F:58:95:42:64:19:BE:D9 |

Play 앱 서명 인증서 지문은 위의 로컬 release 지문과 다를 수 있습니다.

- Firebase SDK가 로그인 세션을 관리합니다. 비밀번호/토큰을 앱 설정에 저장하지 않습니다.
- 이메일 회원가입은 별도 FirebaseAuth 인스턴스에서 계정을 생성하고 인증 메일을 요청한 뒤
  해당 세션을 종료합니다. 앱 로그인 상태는 바뀌지 않으며 사용자가 직접 로그인해야 합니다.
- Google 로그인은 익명 계정이 있으면 기존 UID 연결을 시도합니다.
- 로그인과 마이페이지는 별도 Activity입니다. 마이페이지에서 인증 메일 재전송/인증 확인,
  이메일 계정 비밀번호 재설정, 로그아웃, 확인 후 계정 삭제를 제공합니다.
- 계정 삭제는 Authentication 계정을 삭제합니다. 로컬 및 Firestore 과제의 삭제는 포함하지
  않으며 확인창에 명시합니다. 최근 로그인이 필요하면 다시 로그인하도록 안내합니다.
- 이미 존재하는 계정으로 로그인하면 해당 계정 UID를 사용합니다. 익명 클라우드 데이터의
  자동 병합은 하지 않습니다.
- 기존 로컬 과제 저장 형식은 유지됩니다. 로그인/로그아웃해도 기기의 과제는 남습니다.
  현재 로컬 과제는 계정별로 분리되지 않습니다. 공용 기기 사용 전 이 점을 고려해야 합니다.
- 로그인 성공 시 클라우드 과제를 자동으로 가져오거나 업로드하지 않습니다.
- Firestore의 사용자별 데이터는 users/{uid}/assignments 경로와 UID 기반 보안 규칙을
  별도로 점검해야 합니다. 로그인만으로 규칙이 바뀌지는 않습니다.

## 실제 기기 검증

- 새 이메일 회원가입 → 인증 메일 확인 → 로그인 화면 유지 → 직접 로그인 → 마이페이지.
- 잘못된 비밀번호, 빈 이메일, 중복 가입, 오프라인 오류 및 재설정 메일.
- Google Play 서비스가 있는 기기에서 계정 선택, 취소, 로그인, 로그아웃, 재로그인.
- 이메일 인증 메일, 큰 글꼴, 키보드 표시 및 화면 회전.
- 로그인 화면을 열고 닫아도 캘린더 작성 중 입력 및 기존 과제가 유지되는지 확인.

공식 문서: https://firebase.google.com/docs/auth/android/google-signin
