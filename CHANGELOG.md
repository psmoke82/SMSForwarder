# 📋 SMSForwarder 변경 이력 (Changelog)

---

## [v1.0.6] - 2026-08-07

### 🎨 앱 아이콘 및 브랜드
- **Modern Navy Envelope 모던 네이비 레터 아이콘** 최종 세팅
- 딥 네이비 라운디드 스퀘어 배경 + 실버-화이트 레터 봉투 및 입체 전송 화살표 심볼
- 모든 밀도 계층(`mipmap-mdpi` ~ `xxxhdpi` 및 `drawable`) 자원 자동 동기화

### 📱 UI / UX 개선
- **메인 드로어 메뉴 개편**: 상단 구분선 제거, 은은한 회색조(`alpha = 0.45f`) 텍스트 및 릴리즈 날짜 자동 표기 (`v1.0.6 (Build 6) • 2026-08-07`)
- **포워딩 필터 목록 카드**: 시인성 높은 타이틀 폰트 및 `FilterList` 아이콘 배치
- **포워딩 로그 카드**: 기본 컴팩트 표시 + 클릭 시 상세 원본 내용 아코디언 토글

### 🛡️ 핵심 로직 (Core Engine)
- **중복 포워딩 방지**: `MessageDeduplicator` 5초 메모리 캐시 싱글톤 적용으로 연달아 오는 중복 알림 차단
- **연락처 자동 연동**: `ContactUtils`를 통해 수신 전화번호를 단말기 연락처 이름과 대조하여 `010-1234-5678 (홍길동)` 형태로 자동 변환

### ⚙️ CI / Build & Release
- Gradle 기반 Git 커밋 수 자동 추적 (`versionCode` & `versionName` 수동 지정 불필요)
- `BUILD_DATE` BuildConfig 자동 생성 연동
- GitHub Repository 태그 (`v1.0.6`) 및 자동 릴리즈 가이드 동기화
