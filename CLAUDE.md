# CLAUDE.md — 프로젝트 기본 설정

## 프로젝트 개요
- **프로젝트명**: csa_backend
- **프레임워크**: Spring Boot 4.x (Java 17)
- **역할**: csa_frontend(동화 앱)의 REST API 백엔드 서버

## 상세 설정 파일 참조
모든 세부 규칙은 `claude-config/` 폴더 내 파일을 참조한다.

| 파일 | 내용 |
|------|------|
| [security.md](claude-config/security.md) | 보안 및 권한 설정 |
| [code-guidelines.md](claude-config/code-guidelines.md) | 코드 기본 룰 |
| [style-guidelines.md](claude-config/style-guidelines.md) | 스타일 기본 룰 |
| [project-overview.md](claude-config/project-overview.md) | 프로젝트 현황 및 설명 |

## 핵심 원칙
1. 요청된 것만 변경한다 — 과도한 리팩터링 금지
2. 불필요한 주석·Javadoc 추가 금지
3. 보안 취약점(XSS, SQL Injection, OWASP Top 10 등) 코드 작성 금지
4. 커밋은 명시적으로 요청받을 때만 생성한다

---

# csa_backend

Spring Boot 4.x 기반 백엔드 서버 프로젝트.

## 기술 스택

- **Java 17**
- **Spring Boot 4.0.3**
- **Spring Security**
- **Spring Data JPA**
- **PostgreSQL**
- **Gradle**
- **Docker Compose** (로컬 DB)

## 프로젝트 실행

```bash
./gradlew bootRun
```

로컬 실행 시 `compose.yaml`의 PostgreSQL 컨테이너가 자동으로 함께 기동됩니다 (`spring-boot-docker-compose` 의존성).

## 프로파일 구조

| 프로파일 | 파일 | 용도 |
|---|---|---|
| `local` | `application-local.yaml` | 로컬 개발 (Docker Compose DB 사용) |
| `dev` | `application-dev.yaml` | 개발 서버 배포 |
| `prod` | `application-prod.yaml` | 운영 서버 배포 |

기본 활성 프로파일은 `local`입니다 (`application.yaml`에서 설정).

프로파일 변경 방법:
```bash
# 실행 시 지정
./gradlew bootRun --args='--spring.profiles.active=dev'

# 환경변수로 지정
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

## 환경변수 (prod 프로파일)

운영 환경에서는 아래 환경변수를 반드시 설정해야 합니다:

| 변수명 | 설명 |
|---|---|
| `DB_USERNAME` | PostgreSQL 사용자명 |
| `DB_PASSWORD` | PostgreSQL 비밀번호 |

## 패키지 구조

```
src/main/java/org/example/csa_backend/
```

## 코드 컨벤션

- 패키지명: 소문자 + 언더스코어 없이
- 클래스명: PascalCase
- 메서드/변수명: camelCase
- Lombok 사용 (`@Getter`, `@Setter`, `@RequiredArgsConstructor` 등)
- Controller → Service → Repository 레이어 구조 준수
- REST API 응답은 공통 응답 객체 사용

## 빌드

```bash
./gradlew build
```

## 테스트

```bash
./gradlew test
```
