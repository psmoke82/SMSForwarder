# 📱 SMSForwarder (SMS & 알림 자동 포워딩 앱)

![GitHub Tag](https://img.shields.io/github/v/tag/psmoke82/SMSForwarder?color=blue&label=version)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![License](https://img.shields.io/badge/License-MIT-orange)

**SMSForwarder**는 수신된 SMS 문자 메시지 및 단말기의 다양한 앱(카카오톡, 은행/카드사 결제 알림 등) 알림을 실시간 감지하여 지정된 수신 번호로 자동 포워딩해주는 Android 애플리케이션입니다.

---

## 🌟 주요 기능 (Key Features)

- 📩 **SMS & 앱 알림 실시간 감지**: OS 수준의 SMS 수신기(`SMS_RECEIVED`) 및 알림 접근 서비스(`NotificationListenerService`)를 통한 철저한 메시지 캐칭.
- 🛡️ **스마트 중복 전송 방지 (Deduplication Cache)**: 동일한 알림이나 문자가 수 초 내 연속 수신될 때 발생할 수 있는 중복 발송을 메모리 캐시 기반으로 자동 차단.
- 👤 **연락처 이름 자동 연동**: 수신 전화번호를 단말기 연락처 DB와 대조하여 `010-1234-5678 (홍길동)` 형태로 자동 병기.
- 🎯 **정밀한 포워딩 필터 지원**:
  - **대상 앱 지정**: 특정 앱(삼성 메시지, 카카오톡, 금융 앱 등) 또는 전체 앱 대상 포워딩.
  - **키워드 조건 설정**: 포착 키워드(AND / OR 조합) 및 포함 시 전송을 거부하는 **제외 키워드** 설정.
- 📝 **커스텀 메시지 템플릿 치환**: `{appName}`, `{title}`, `{body}`, `{filterName}`, `{timestamp}` 치환 태그를 활용한 가공 메시지 발송.
- 📊 **컴팩트 실행 기록 관리**: 전송 성공/실패 여부, 원본 알림 내용, 전송 시각, 에러 원인을 한눈에 확인할 수 있는 실행 로그 탭.
- 🔋 **포그라운드 서비스 동작 지원**: 상단바 고정 알림을 통해 OS의 수면/절전 모드에 의한 서비스 종료 없이 24시간 안정적인 포워딩 유지.
- 💾 **설정 백업 및 복구**: 등록된 포워딩 필터 및 앱 설정 백업/복구 기능 제공.

---

## 🛠️ 기술 스택 (Tech Stack)

| 구분 | 기술 스택 |
| :--- | :--- |
| **언어 (Language)** | Kotlin |
| **UI 프레임워크** | Jetpack Compose, Material 3 Design |
| **아키텍처** | Clean Architecture, MVVM (ViewModel, StateFlow) |
| **데이터베이스** | Room DB |
| **비동기 처리** | Kotlin Coroutines & Flow |
| **최소 / 타겟 SDK** | Min SDK 26 (Android 8.0) / Target SDK 34 (Android 14) |

---

## 🔒 필수 권한 (Permissions)

앱의 정상적인 작동을 위해 다음 권한이 필요합니다:

1. **SMS 관련 권한 (`RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`)**: SMS 문자를 감지하고 타 수신자에게 전송하기 위한 필수 권한.
2. **알림 접근 허용 (`BIND_NOTIFICATION_LISTENER_SERVICE`)**: 카카오톡, 카드사 등 앱 알림을 캐치하기 위한 설정.
3. **연락처 읽기 권한 (`READ_CONTACTS`)**: 수신 번호에 대응하는 연락처 이름을 자동 조회하여 표기하기 위한 권한.
4. **알림 게시 권한 (`POST_NOTIFICATIONS`)**: Android 13(API 33) 이상에서 포그라운드 감시 서비스 알림을 띄우기 위한 권한.

---

## 📂 프로젝트 구조 (Project Structure)

```text
com.example.smsforwarder
├── data
│   ├── local (Room Database, Entity, DAO)
│   ├── model (BackupPayload 등)
│   └── repository (ForwarderRepository, UserPreferencesRepository)
├── domain
│   ├── model (Filter, ForwardLog 등 도메인 객체)
│   └── parser (FilterEvaluator, TemplateParser, MessageDeduplicator)
├── service
│   ├── SmsReceiver (SMS 수신 브로드캐스트)
│   ├── SmsNotificationListenerService (알림 리스너)
│   ├── ForwarderForegroundService (포그라운드 서비스)
│   └── SmsSender (SMS 발송 헬퍼)
├── ui
│   ├── components (공통 다이얼로그 & 컴포넌트)
│   ├── navigation (Compose NavGraph)
│   ├── screen (필터 목록, 필터 편집, 실행 기록 등 화면)
│   └── MainViewModel (상태 관리 ViewModel)
└── utils
    └── ContactUtils (연락처 이름 조회 유틸)
```

---

## 🚀 시작하기 (Getting Started)

1. 저장소를 클론(Clone)합니다:
   ```bash
   git clone https://github.com/psmoke82/SMSForwarder.git
   ```
2. Android Studio에서 프로젝트를 오픈한 후 Gradle Sync를 수행합니다.
3. 단말기(또는 에뮬레이터)를 연결하고 빌드 및 실행합니다:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 라이선스 (License)

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
