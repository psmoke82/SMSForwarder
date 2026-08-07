# 📋 SMSForwarder 변경 이력 (Changelog)

---

## [v1.0.11] - 2026-08-07

### 📐 UI / UX 슬림화 및 콤팩트 디자인 개편
- **필터 목록 카드 레이아웃 정돈**: 카드 상하 내부 여백 축소 (`16.dp` ➡️ `10.dp`) 및 필터 이름 ↔ 수신처 간격 축소 (`10.dp` ➡️ `2.dp`)
- **포워딩 실행 기록 카드 높이 60% 슬림화**: 기존 `SuggestionChip`을 콤팩트 **미니 배지(`Surface`)**로 교체하고 접기/펼치기 아이콘을 동일 라인에 통합
- **실시간 발송 텍스트 미리보기 샘플 개선**: 여러 앱 선택 시 콤마 목록 대신 첫 번째 대표 앱 이름 **1개만 샘플로 가공**되어 표시되도록 개선

### 🏷️ 빌드 & 바이너리 자동화 (Build & Release Automation)
- Gradle 빌드 시 출력 APK 파일명이 `SMSForwarder-v1.0.11-rel.apk`로 자동 지정
- GitHub Release 생성 시 `SMSForwarder-v1.0.11-rel.apk` 바이너리 자동 업로드

---

## [v1.0.8] - 2026-08-07

### 🏷️ 빌드 & 바이너리 자동화
- 버전 포함 APK 파일명 산출 설정 적용 (`androidComponents`)
- GitHub Release 자동 바이너리 게시 규칙 등록
