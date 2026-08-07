# 📋 SMSForwarder 변경 이력 (Changelog)

---

## [v1.0.8] - 2026-08-07

### 🏷️ 빌드 & 바이너리 자동화 (Build & Release Automation)
- **버전 포함 APK 파일명 산출 설정**: Gradle 빌드 시 출력 APK 파일명이 `SMSForwarder-v1.0.[커밋수]-rel.apk` (예: `SMSForwarder-v1.0.8-rel.apk`) 형식으로 자동 세팅되도록 [app/build.gradle.kts](file:///c:/Users/sm1021.park/AndroidStudioProjects/SMSForwarder/app/build.gradle.kts) 설정 (`androidComponents`)
- **GitHub Release 자동 바이너리 게시**: GitHub Release 생성 시 `SMSForwarder-v1.0.8-rel.apk` 바이너리가 자동으로 업로드되도록 구현

---

## [v1.0.7] - 2026-08-07

### 🎨 앱 아이콘 및 브랜드
- **Modern Navy Envelope 모던 네이비 레터 아이콘** 세팅
- 딥 네이비 라운디드 스퀘어 배경 + 실버-화이트 레터 봉투 및 입체 전송 화살표 심볼

### 📱 UI / UX 개선
- **메인 드로어 메뉴 개편**: 상단 구분선 제거, 은은한 회색조(`alpha = 0.45f`) 텍스트 및 릴리즈 날짜 자동 표기 (`v1.0.8 (Build 8) • 2026-08-07`)

### 🛡️ 핵심 로직 (Core Engine)
- **중복 포워딩 방지**: `MessageDeduplicator` 5초 메모리 캐시 싱글톤 적용
- **연락처 자동 연동**: `ContactUtils`를 통해 수신 전화번호를 단말기 연락처 이름과 대조하여 `010-1234-5678 (홍길동)` 형태로 자동 변환
