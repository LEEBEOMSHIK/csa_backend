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

```bash
./gradlew bootRun
```

### local — Docker Compose 전체 실행

백엔드 앱과 PostgreSQL을 모두 컨테이너로 기동합니다.

```bash
docker compose up --build
```

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
| `local` | `application-local.yaml` | `compose.yaml` | 로컬 개발 (기본값) |
| `dev` | `application-dev.yaml` | `compose.dev.yaml` | 개발 서버 배포 |
| `prod` | `application-prod.yaml` | `compose.prod.yaml` | 운영 서버 배포 |

각 환경의 DB 접속 정보·JWT Secret은 해당 `application-{profile}.yaml` 파일에서 관리합니다.

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
│       ├── application-local.yaml
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
