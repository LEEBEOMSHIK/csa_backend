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
| [oauth-guidelines.md](claude-config/oauth-guidelines.md) | OAuth 제공자별 설계 및 구현 기준 |
| [db-guidelines.md](claude-config/db-guidelines.md) | DB 및 테이블 생성·수정·삭제 규칙, 계정 권한 |

## 핵심 원칙
1. 요청된 것만 변경한다 — 과도한 리팩터링 금지
2. 불필요한 주석·Javadoc 추가 금지
3. 보안 취약점(XSS, SQL Injection, OWASP Top 10 등) 코드 작성 금지
4. 커밋은 명시적으로 요청받을 때만 생성한다

---

## 트러블슈팅

### [Docker] `database "xxx" does not exist` 오류

**증상**
```
FATAL: database "xxx" does not exist
```
백엔드 컨테이너가 기동 직후 종료되고 위 에러가 로그에 출력된다.

**원인**
`application-*.yaml` 또는 `compose.yaml`의 DB명을 변경했을 때, 기존 postgres 볼륨(`postgres_data`)이 이전 DB명으로 이미 초기화되어 있으면 `POSTGRES_DB` 환경변수가 재적용되지 않는다.
postgres는 볼륨 데이터가 존재하면 초기화 스크립트를 다시 실행하지 않는다.

**즉시 해결**
```bash
docker compose down -v   # 볼륨까지 삭제
docker compose up -d --build
```

**주의**: `-v` 옵션은 볼륨의 모든 데이터를 삭제한다. 보존이 필요한 데이터가 있으면 먼저 백업한다.

**예방**
`compose.yaml`의 `POSTGRES_DB` 값과 `application-local.yaml`의 datasource URL DB명을 항상 동일하게 유지한다.

---

### [Docker] `down -v` 후 Entity 컬럼 누락

**증상**
`docker compose down -v` 후 재기동 시 Entity에 추가한 컬럼이 테이블에 생성되지 않는다.

**원인**
`docker compose up -d`에서 `--build`를 생략하면 **캐시된 구 이미지**로 기동된다.
Hibernate `ddl-auto: update`는 이미지 안의 Entity 기준으로 테이블을 생성하기 때문에,
코드 변경 전 이미지에는 새 컬럼 정보가 없어 생성되지 않는다.

**즉시 해결**
```bash
docker compose down -v
docker compose up -d --build   # --build 필수
```

**규칙**: `docker compose down -v` 이후 재기동은 **반드시 `--build`를 붙인다**.

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
