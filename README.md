# csa_backend

**동화 만들기 앱 (csa_frontend)** 의 REST API 백엔드 서버입니다.
어린이 동화 앱의 AI 동화 생성, 캐릭터 관리, 사용자 인증 등 핵심 기능을 제공합니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.3 |
| Build | Gradle |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Auth | Spring Security + JWT |
| Local DB | Docker Compose |

---

## 시작하기

### 사전 요구사항

- Java 17
- Docker Desktop

---

## 실행 방법

### local — Gradle 직접 실행

PostgreSQL은 `spring-boot-docker-compose`가 `compose.yaml`의 컨테이너를 자동 기동합니다.
로컬 포트는 다른 프로젝트와 충돌하지 않도록 백엔드 `18080`, PostgreSQL `15432`를 사용합니다.
비민감 local 설정은 tracked `application.yaml`의 `local` 문서에 있으며,
`application-local.yaml`을 만들거나 복사하지 않습니다. DB 비밀번호와 사용자 백엔드 JWT secret은
현재 프로세스 환경변수로만 주입합니다.

```powershell
$secureDbPassword = Read-Host 'Local DB password (reuse it for the existing postgres volume)' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secureDbPassword).Password
$jwtBytes = [byte[]]::new(48)
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($jwtBytes)
$random.Dispose()
$env:CSA_USER_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
.\gradlew.bat bootRun
```

기존 PostgreSQL volume이 있으면 최초 초기화 때 사용한 DB 비밀번호를 다시 입력해야 합니다.
비밀번호가 맞지 않아도 volume을 임의로 삭제하지 마세요. 환경변수는 이 PowerShell 프로세스에만 남으며,
실행 shell을 닫으면 다시 설정해야 합니다.

### local — Docker Compose 전체 실행

백엔드 앱과 PostgreSQL을 모두 컨테이너로 기동합니다.
위 PowerShell block으로 `DB_PASSWORD`와 `CSA_USER_JWT_SECRET`을 먼저 설정한 뒤 실행합니다.

```powershell
docker compose --profile full up --build
```

컨테이너 실행 후 백엔드는 `http://localhost:18080`에서 접근합니다.

### dev — Docker Compose 실행

```bash
docker compose -f compose.dev.yaml up --build
```

### prod — Docker Compose 실행

```bash
docker compose -f compose.prod.yaml up --build
```

---

## 프로파일 구조

| 프로파일 | 설정 파일 | compose 파일 | 용도 |
|----------|-----------|--------------|------|
| `local` | `application.yaml`의 `local` 문서 | `compose.yaml` | 로컬 개발 (기본값) |
| `dev` | `application-dev.yaml` | `compose.dev.yaml` | 개발 서버 배포 |
| `prod` | `application-prod.yaml` | `compose.prod.yaml` | 운영 서버 배포 |

local의 비민감 접속 정보는 tracked config에서 관리하고, `DB_PASSWORD`와
`CSA_USER_JWT_SECRET`은 기본값 없는 필수 환경변수입니다. 운영 자격 증명도 repository file에 저장하지 않습니다.

---

## 결제 검증 설정

`prod` 프로파일에서는 Google Play 영수증을 Android Publisher API로 검증합니다. 로컬·dev에서는 stub 검증기가 활성화되어 실제 스토어 없이 `/subscriptions/verify` 흐름을 테스트할 수 있습니다.

| 환경변수 | 설명 |
|----------|------|
| `STORE_GOOGLE_PACKAGE_NAME` | Play Console에 등록된 Android applicationId |
| `STORE_GOOGLE_SERVICE_ACCOUNT_EMAIL` | Android Publisher API 권한이 있는 서비스 계정 이메일 |
| `STORE_GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY` | 서비스 계정 private key. 줄바꿈은 `\n` 문자열로 주입 가능 |

프론트엔드 상품 ID는 `premium_monthly`입니다. Play Console의 구독 product ID도 동일해야 합니다.

---

## API 구조

### 인증 (Auth)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/login` | 로그인 (JWT 발급) |
| POST | `/auth/refresh` | 토큰 갱신 |
| GET | `/users/me` | 내 정보 조회 |

### 동화 (Fairytale)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/fairytales/generate` | AI 동화 생성 |
| GET | `/fairytales` | 동화 목록 조회 |
| GET | `/fairytales/{id}` | 동화 상세 조회 |
| POST | `/fairytales/{id}/share` | 동화 공유 |
| DELETE | `/fairytales/{id}` | 동화 삭제 |

### 캐릭터 (Character)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/characters` | 캐릭터 저장 |
| GET | `/characters` | 내 캐릭터 목록 조회 |
| GET | `/characters/{id}` | 캐릭터 상세 조회 |
| DELETE | `/characters/{id}` | 캐릭터 삭제 |

### 찜 (Favorites)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/favorites/{fairytaleId}` | 찜 추가 |
| DELETE | `/favorites/{fairytaleId}` | 찜 제거 |
| GET | `/favorites` | 찜 목록 조회 |

---

## 프로젝트 구조

```
src/
├── main/
│   ├── java/org/example/csa_backend/
│   │   ├── domain/
│   │   │   ├── fairytale/        # 동화 도메인
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   └── entity/
│   │   │   ├── character/        # 캐릭터 도메인
│   │   │   ├── user/             # 사용자·인증 도메인
│   │   │   └── favorites/        # 찜 도메인
│   │   ├── global/
│   │   │   ├── config/           # Security, CORS 설정
│   │   │   ├── exception/        # 전역 예외 처리
│   │   │   ├── response/         # 공통 API 응답 (ApiResponse<T>)
│   │   │   └── util/
│   │   └── CsaBackendApplication.java
│   └── resources/
│       ├── application.yaml
│       ├── application-dev.yaml
│       └── application-prod.yaml
└── test/
    └── java/org/example/csa_backend/
```

---

## 빌드 & 테스트

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# Docker 이미지 빌드
docker build -t csa_backend .
```

---

## 개발 현황

- [x] 프로젝트 초기 세팅 (Spring Boot 4.0.3)
- [x] Docker Compose DB 연동
- [x] 프로파일 구조 구성 (local / dev / prod)
- [ ] 패키지 구조 재편 (domain 기반 feature 구조)
- [ ] 공통 응답 객체 (`ApiResponse<T>`) 구현
- [ ] 전역 예외 처리 (`GlobalExceptionHandler`) 구현
- [ ] Spring Security + JWT 인증·인가 구현
- [ ] 동화 생성 API (LLM 연동)
- [ ] 동화 목록·상세·공유 API
- [ ] 캐릭터 CRUD API
- [ ] 찜 API
- [ ] 단위 테스트·통합 테스트
- [ ] dev / prod 서버 배포

---

## 프론트엔드 연동

- **연동 프로젝트**: `csa_frontend` (Flutter 동화 앱)
- **통신 방식**: REST API (JSON)
- **인증**: JWT Bearer Token
- **CORS**: 프론트엔드 도메인 명시적 허용
