# 수행평가 캘린더 Android 앱

이 폴더는 수행평가 캘린더의 Android 앱 프로젝트입니다.
현재 앱 화면은 외부 웹사이트를 WebView로 감싸지 않고, Android 코드에서 직접 구성합니다.

## Android Studio에서 실행하기

1. Android Studio 설치
2. `settings.gradle`이 있는 프로젝트 폴더 열기 (GitHub에서 받았다면 저장소 최상위 폴더)
   - Firebase 콘솔에서 Android 앱의 `google-services.json`을 받아 `app/`에 넣습니다. 개인 설정 파일은 저장소에 포함하지 않습니다.
3. Gradle Sync 실행
4. Android 기기 또는 에뮬레이터 선택
5. Run 클릭

## 특징

- Android 네이티브 화면
- 수행평가 등록, 수정, 삭제, 완료 처리
- 이번 주 수행평가 표시
- 월간 캘린더 표시
- 사진 업로드 지원
- 앱 내부 저장소에 수행평가 데이터 저장
- 이메일·Google 로그인과 프로필 설정
- 공개 수행평가 저장·가져오기 (비공개 과제와 사진은 휴대폰에만 저장)
- 휴대폰 자체 마감 알림, 알림 테스트와 권한 설정

## 빌드와 참고 문서

Java 17 이상과 Android SDK 35를 설치한 뒤 Windows에서 ` .\gradlew.bat assembleDebug`를 실행합니다.
SDK 경로는 Android Studio가 생성하는 `local.properties`에 설정합니다.
배포용 서명키와 `release-signing.properties`는 따로 보관하며 GitHub에 올리지 않습니다.

- [알림 사용법과 검증 범위](LOCAL_REMINDERS.md)
- [Firebase 로그인 설정](FIREBASE_LOGIN_SETUP.md)
- [공개 수행평가 저장](ASSIGNMENTS_SYNC.md)
- [프로필 화면 검증](PROFILE_UI_QA.md)

## 주의

- 이 앱은 더 이상 `https://report-calendar.vercel.app` 화면에 의존하지 않습니다.
- 앞으로 화면 수정은 `app/src/main/java`와 `app/src/main/res` 안의 Android 파일을 기준으로 진행합니다.
