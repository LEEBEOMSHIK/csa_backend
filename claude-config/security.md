# security.md — 보안 및 권한 설정

## 1. 기본 원칙
- 보안 취약점(OWASP Top 10 기준)을 유발하는 코드를 작성하지 않는다.
- 인증·인가 로직은 항상 서버 사이드에서 최종 검증한다.
- 민감한 정보(API Key, Secret, DB 비밀번호, JWT Secret 등)는 소스코드에 하드코딩하지 않는다.

## 2. 민감 정보 관리
- API Key, 비밀번호, JWT Secret 등은 **절대 소스코드에 포함하지 않는다**.
- 환경 변수 또는 Spring 프로파일(`application-prod.yaml`)을 통해 주입한다.
  ```bash
  export DB_USERNAME=your_username
  export DB_PASSWORD=your_password
  ```
- `.env` 파일이나 `application-local.yaml`은 반드시 `.gitignore`에 등록한다.
- 운영 환경의 시크릿은 Secret Manager(AWS Secrets Manager 등)를 통해 관리한다.

## 3. Spring Security 설정
- Spring Security 기본 설정을 비활성화하지 않는다.
- CSRF 보호는 REST API 특성에 맞게 적절히 설정한다 (JWT 사용 시 Stateless로 비활성화 가능).
- CORS 설정은 허용 오리진을 명시적으로 지정하고, `*` 와일드카드는 운영 환경에서 금지한다.
- 비밀번호는 반드시 `BCryptPasswordEncoder` 등 강력한 해시 알고리즘으로 암호화한다.

## 4. JWT / 인증
- JWT Secret은 충분한 길이(256비트 이상)의 랜덤 값을 사용한다.
- 토큰 만료 시간을 반드시 설정하고, Refresh Token 전략을 사용한다.
- 토큰은 응답 헤더(`Authorization: Bearer ...`)로 전달하고, 로컬 스토리지 저장을 권장하지 않는다.

## 5. 입력 값 검증
- 외부에서 들어오는 모든 입력값은 시스템 경계(Controller 레이어)에서 검증한다.
- `@Valid` / `@Validated` + Bean Validation을 사용한다.
- 사용자 입력을 직접 JPQL·네이티브 쿼리에 삽입하지 않는다 — PreparedStatement / JPA 파라미터 바인딩을 사용한다.

## 6. SQL Injection 방지
- JPA / Spring Data Repository를 우선 사용한다.
- 네이티브 쿼리가 필요한 경우 반드시 파라미터 바인딩(`:param` 또는 `?1`)을 사용한다.
- 사용자 입력을 `String.format()` 또는 문자열 연결로 쿼리에 삽입하지 않는다.

## 7. 로깅 보안
- `System.out.println()` 또는 로그에 민감 정보(비밀번호, 토큰, 개인정보)를 출력하지 않는다.
- 운영 환경 로그 레벨은 `INFO` 이상으로 설정한다.

## 8. 코드 리뷰 체크리스트
- [ ] 하드코딩된 시크릿이 없는가?
- [ ] SQL Injection 취약 코드가 없는가?
- [ ] CORS 허용 오리진이 명시적으로 지정되었는가?
- [ ] 민감 정보가 로그에 출력되지 않는가?
- [ ] Bean Validation이 Controller 입력에 적용되었는가?
- [ ] 서드파티 라이브러리의 보안 취약점(CVE)을 확인했는가?
