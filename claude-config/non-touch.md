---
name: non-touch
description: Files and areas that must not be analyzed or modified by Claude
type: reference
---

# No Analysis and Modification

아래 항목들은 Claude가 분석하거나 수정해서는 안 된다.

## 절대 수정 금지 파일

- `.gitignore` (루트 및 프로젝트)
- `gradle/wrapper/gradle-wrapper.jar`
- `gradlew`, `gradlew.bat`

## 절대 수정 금지 영역

- `build/` — 빌드 산출물 디렉토리
- `.gradle/` — Gradle 캐시
- `.idea/` — IDE 설정

## 주의 사항

- `application-prod.yaml` — 운영 환경 설정, 명시적 요청 없이 수정 금지
- `compose.yaml` — 로컬 DB 설정, 명시적 요청 없이 수정 금지
- `CLAUDE.md`, `claude-config/` — 가이드라인 파일, 명시적 요청 없이 수정 금지
