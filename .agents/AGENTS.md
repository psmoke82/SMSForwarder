# Project Rules for SMSForwarder

공통 개발/릴리즈 규칙은 전역(`~\.gemini\config\AGENTS.md`)을 따릅니다.
아래는 Android 공통 규칙(`~\.gemini\config\rules\android-template.md`에서 복사됨, 도구가
import를 지원하지 않아 수동 복사 — 원본 수정 시 이 섹션도 수동으로 재반영 필요)과
SMSForwarder 프로젝트 고유 규칙입니다.

## Android 공통 규칙

### 버전 정책

1. **버전 형식**: `v[Major].[Minor].[Patch]`
2. **Patch(마지막 숫자)**: 직전 릴리즈 대비 **순차적으로 +1 자동 증가**시킵니다.
3. **Major/Minor**: 사용자가 명시적으로 상향을 지정한 경우에만 올립니다.
4. **`versionCode`(빌드 번호)**: 버전명의 Patch 숫자와는 별개로, 기존 값보다 항상 증가하는 정수로 관리합니다.

### Gradle 빌드 규칙

- 빌드 전 `./gradlew --stop`, 빌드 중 강제종료 금지
- 실패 시 캐시/환경 문제(cannot find symbol 등)와 실제 코드 문제를 구분
- 원인 불명 시 `app/build`, `.gradle` 삭제 후 클린 빌드
- 세션 종료 전 `./gradlew --stop`

## SMSForwarder 프로젝트 고유 규칙

1. **버전 표기 갱신 위치**: `app\build.gradle.kts`의 `versionName`/`versionCode`(및 `BUILD_DATE` buildConfigField)를 갱신하면, 네비게이션 드로어 하단(`ui\navigation\NavGraph.kt`)의 `BuildConfig.VERSION_NAME`/`VERSION_CODE`/`BUILD_DATE` 표시가 자동 반영됩니다. (SoundLog와 달리 화면단 하드코딩 fallback 없음)
2. **릴리즈 산출물 명명**: `releases/SMSForwarder-v[Major].[Minor].[Patch]-rel.apk`
3. **Release 제목 예시**: `--title "SMSForwarder v1.0.21"`
4. 별도 로컬 테스트 빌드/사이드로드 절차 없이, 명시적 릴리즈 요청 시 바로 정식 빌드로 진행합니다.
