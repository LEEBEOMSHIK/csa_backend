# project-overview.md — 프로젝트 현황 및 설명

## 1. 프로젝트 기본 정보
| 항목 | 내용 |
|------|------|
| 프로젝트명 | csa_backend |
| 서버 이름 | 동화 만들기 앱 백엔드 (Fairy Tale App Backend) |
| 프레임워크 | Spring Boot 4.0.3 |
| 언어 | Java 17 |
| 빌드 도구 | Gradle |
| 데이터베이스 | PostgreSQL |
| 로컬 DB 실행 | Docker Compose (`compose.yaml`) |

## 2. 프로젝트 목적
`csa_frontend` (Flutter 동화 앱)의 REST API 백엔드 서버.
어린이 동화 앱의 핵심 기능을 제공한다:
- AI 동화 생성 (LLM 연동)
- 캐릭터 저장·관리
- 동화 목록 조회·공유
- 사용자 인증·인가

## 3. 주요 API 구조

### 동화 (Fairytale)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/fairytales/generate` | AI 동화 생성 |
| GET | `/fairytales` | 동화 목록 조회 |
| GET | `/fairytales/{id}` | 동화 상세 조회 |
| POST | `/fairytales/{id}/share` | 동화 공유 |
| DELETE | `/fairytales/{id}` | 동화 삭제 |

### 캐릭터 (Character)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/characters` | 캐릭터 저장 |
| GET | `/characters` | 내 캐릭터 목록 조회 |
| GET | `/characters/{id}` | 캐릭터 상세 조회 |
| DELETE | `/characters/{id}` | 캐릭터 삭제 |

### 사용자 (User)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/login` | 로그인 (JWT 발급) |
| POST | `/auth/refresh` | 토큰 갱신 |
| GET | `/users/me` | 내 정보 조회 |

### 찜 (Favorites)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/favorites/{fairytaleId}` | 찜 추가 |
| DELETE | `/favorites/{fairytaleId}` | 찜 제거 |
| GET | `/favorites` | 찜 목록 조회 |

## 4. 기술 스택 상세

### 데이터베이스
- **PostgreSQL**: 메인 데이터 저장소
- **Spring Data JPA**: ORM
- **Docker Compose**: 로컬 개발 DB 환경

### 인증
- **Spring Security**: 인증·인가 프레임워크
- **JWT**: Stateless 인증 토큰

### AI 연동
- **LLM API**: AI 동화 생성 (OpenAI GPT 또는 Claude API — 미확정)
- HTTP 클라이언트: `WebClient` (Spring WebFlux) 또는 `RestTemplate`

## 5. 프로파일 구조
| 프로파일 | 파일 | 용도 |
|---|---|---|
| `local` | `application-local.yaml` | 로컬 개발 (Docker Compose DB) |
| `dev` | `application-dev.yaml` | 개발 서버 배포 |
| `prod` | `application-prod.yaml` | 운영 서버 배포 |

## 6. 디렉터리 구조 (목표)
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
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   └── entity/
│   │   │   ├── user/             # 사용자·인증 도메인
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   └── entity/
│   │   │   └── favorites/        # 찜 도메인
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       └── entity/
│   │   ├── global/
│   │   │   ├── config/           # Security, CORS, JPA Auditing 설정
│   │   │   ├── exception/        # 커스텀 예외 클래스
│   │   │   ├── response/         # ApiResponse 공통 객체
│   │   │   └── util/             # 유틸리티
│   │   └── CsaBackendApplication.java
│   └── resources/
│       ├── application.yaml
│       ├── application-local.yaml
│       ├── application-dev.yaml
│       └── application-prod.yaml
└── test/
    └── java/org/example/csa_backend/
        └── (소스 구조와 동일하게 구성)
```

## 7. 현재 개발 단계
- [x] 프로젝트 초기 세팅 (Spring Boot)
- [x] Docker Compose DB 연동 (`compose.yaml`)
- [x] 프로파일 구조 구성 (local / dev / prod)
- [x] Claude 설정 파일 구성
- [ ] 패키지 구조 재편 (domain 기반 feature 구조)
- [ ] 공통 응답 객체 (`ApiResponse<T>`) 구현
- [ ] 전역 예외 처리 (`GlobalExceptionHandler`) 구현
- [ ] 사용자 인증·인가 (Spring Security + JWT) 구현
- [ ] 동화 생성 API 구현 (LLM 연동)
- [ ] 동화 목록·상세·공유 API 구현
- [ ] 캐릭터 CRUD API 구현
- [ ] 찜 API 구현
- [ ] 단위 테스트·통합 테스트 작성
- [ ] 개발 서버 배포 (dev 프로파일)
- [ ] 운영 서버 배포 (prod 프로파일)

## 8. 주요 의존성
| 라이브러리 | 용도 |
|-----------|------|
| spring-boot-starter-web | REST API |
| spring-boot-starter-data-jpa | ORM |
| spring-boot-starter-security | 인증·인가 |
| spring-boot-docker-compose | 로컬 Docker Compose 연동 |
| postgresql | PostgreSQL JDBC 드라이버 |
| lombok | 보일러플레이트 코드 제거 |
| spring-boot-starter-validation | Bean Validation |
| spring-boot-starter-test | 테스트 |

## 9. 프론트엔드 연동
- **연동 대상**: `csa_frontend` (Flutter 동화 앱)
- **통신 방식**: REST API (JSON)
- **인증**: JWT Bearer Token
- **CORS**: 프론트엔드 도메인 명시적 허용

## 10. 알려진 이슈 및 기술 부채
- 패키지 구조가 아직 초기 상태 — domain 기반 feature 구조로 재편 필요
- AI(LLM) 연동 API 미확정 (OpenAI vs Claude)
- JWT 라이브러리 선택 필요 (`jjwt` 또는 `spring-security-oauth2-jose`)

## 11. 향후 계획
- AI 이미지 생성 API 연동 (캐릭터 기반 동화 삽화 자동 생성)
- 실시간 알림 기능 (WebSocket)
- 파일 업로드 (캐릭터 이미지, TTS 오디오) — AWS S3 연동
- 다국어 지원
