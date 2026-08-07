# Project Rules for SMSForwarder

## Release & GitHub Workflow Rules

1. **자동 커밋 & 깃허브 푸시**:
   - 코드 수정을 마칠 때마다 `git add .`, `git commit -m "..."`, `git push origin main` 명령을 통해 깃허브 메인 브랜치에 항상 최신 소스코드를 업로드합니다.

2. **버전 명명 규칙 & 자동 릴리즈 태그 생성**:
   - APK 빌드 시 파일명은 버전과 타입을 포함하여 `SMSForwarder-v1.0.[커밋수]-rel.apk` (예: `SMSForwarder-v1.0.8-rel.apk`) 형식으로 자동 생성합니다.
   - 커밋 후 현재 커밋 수(`git rev-list --count HEAD`)에 맞춰 버전 태그(`v1.0.[커밋수]`)를 자동 생성하고 `git push origin [태그명]`으로 푸시합니다.

3. **변경 내역(Changelog) 작성 및 릴리즈 노트/바이너리 등록**:
   - `CHANGELOG.md` 파일에 이번 버전의 세부 변경 내역(기능 추가, UI 개선, 버그 수정 등)을 명확하게 업데이트합니다.
   - `SMSForwarder-v1.0.[커밋수]-rel.apk` 바이너리 업로드와 함께 깃허브 Release를 자동 게시하고, 사용자에게 릴리즈 페이지 링크 및 상세 **Release Notes** 전문을 제공합니다.
