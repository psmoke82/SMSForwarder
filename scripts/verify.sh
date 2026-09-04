#!/usr/bin/env bash
# 실기기 무선 디버깅(adb over WiFi) 개발 루프 검증 스크립트.
# 코드 수정 후 매번 이 스크립트를 실행해 실기기에서 빌드/설치/실행 결과를 확인한다.

set -e

# Git Bash(MSYS)는 adb 인자로 넘기는 기기(안드로이드) 쪽 절대경로("/sdcard/...")를
# Windows 로컬 경로로 잘못 변환한다 (예: "C:/Program Files/Git/sdcard/..."). 로컬
# 경로(/tmp/verify 등)는 정상 변환되어야 하므로 전역 비활성화 대신, 기기 쪽 경로에만
# 앞에 슬래시를 하나 더 붙이는 표준 MSYS 우회법("//sdcard/...")을 사용한다.
DEVICE_UI_DUMP="//sdcard/ui.xml"

PKG="com.example.smsforwarder"
ACTIVITY=".MainActivity"
DEVICE_IP="${DEVICE_IP:-}"   # 예: DEVICE_IP=192.168.0.23 ./scripts/verify.sh

OUT_DIR="/tmp/verify"
mkdir -p "$OUT_DIR"

if [ -z "$DEVICE_IP" ]; then
    echo "[오류] DEVICE_IP가 설정되지 않았습니다. 예: DEVICE_IP=192.168.0.23 ./scripts/verify.sh"
    exit 1
fi

# 끊겨 있어도 자동 재연결 시도 (이미 연결돼 있으면 무해).
# Android 11+ 무선 디버깅은 페어링 이후 mDNS로 자동 재연결되며 연결 포트가 5555가
# 아닌 경우가 많으므로, 5555 연결은 "밑져야 본전"으로 시도만 하고 실제 판정은
# 아래에서 연결 상태(device)인 트랜스포트가 하나라도 있는지로 확인한다.
adb connect "$DEVICE_IP:5555" > /dev/null 2>&1 || true

# 기기 연결 여부 확인 (IP:5555든 mDNS 시리얼이든, "device" 상태가 하나도 없으면 종료)
if ! adb devices | grep -E "[[:space:]]device$" > /dev/null; then
    echo "[오류] 기기가 연결되지 않았습니다. 'adb devices' 결과:"
    adb devices
    echo ""
    echo "다음 중 하나로 재연결하세요:"
    echo "  1) 폰의 '무선 디버깅 > 페어링 코드로 기기 페어링' 화면의 IP:포트와 6자리 코드로:"
    echo "       adb pair <IP>:<포트>"
    echo "  2) 케이블 연결 후: adb tcpip 5555 (이후 케이블 제거하고 재연결)"
    exit 1
fi

echo "[1/8] 기기 연결 확인 완료:"
adb devices

echo "[2/8] 빌드 및 설치 (installDebug)"
./gradlew installDebug

echo "[3/8] 로그캣 버퍼 초기화"
adb logcat -c

echo "[4/8] 앱 강제 종료 후 재실행"
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$ACTIVITY"

sleep 3

# 이 아래는 로그/화면 수집 단계 — 하나가 실패해도 전체 스크립트가 죽지 않게 처리
set +e

echo "[5/8] 스크린샷 캡처"
adb exec-out screencap -p > "$OUT_DIR/shot.png"

echo "[6/8] UI 계층 덤프 (uiautomator dump)"
adb shell uiautomator dump "$DEVICE_UI_DUMP" && adb pull -a "$DEVICE_UI_DUMP" "$OUT_DIR/"

echo "[7/8] 앱 프로세스 로그 수집"
PID=$(adb shell pidof -s "$PKG")
if [ -n "$PID" ]; then
    adb logcat -d --pid="$PID" > "$OUT_DIR/log.txt"
else
    echo "[경고] 앱 프로세스를 찾을 수 없습니다 (실행 직후 크래시했을 가능성). 전체 로그로 대체 수집합니다."
    adb logcat -d > "$OUT_DIR/log.txt"
fi

echo "[8/8] 크래시 로그 수집"
adb logcat -b crash -d > "$OUT_DIR/crash.txt"

echo ""
echo "=== 결과물: $OUT_DIR/{shot.png, ui.xml, log.txt, crash.txt} ==="
echo "=== log.txt 마지막 50줄 ==="
tail -50 "$OUT_DIR/log.txt"
