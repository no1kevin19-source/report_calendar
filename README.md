# 수행평가 캘린더 Android 앱

이 폴더는 수행평가 캘린더의 Android 앱 프로젝트입니다.
현재 앱 화면은 외부 웹사이트를 WebView로 감싸지 않고, Android 코드에서 직접 구성합니다.

## Android Studio에서 실행하기

1. Android Studio 설치
2. `android-app` 폴더 열기
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

## 주의

- 이 앱은 더 이상 `https://report-calendar.vercel.app` 화면에 의존하지 않습니다.
- 앞으로 화면 수정은 `app/src/main/java`와 `app/src/main/res` 안의 Android 파일을 기준으로 진행합니다.
