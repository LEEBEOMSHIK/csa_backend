# style-guidelines.md — 스타일 기본 룰

## 1. API 응답 형식
- 모든 REST API 응답은 공통 응답 객체로 통일한다.
  ```java
  // 예시: global/response/ApiResponse.java
  @Getter
  @RequiredArgsConstructor
  public class ApiResponse<T> {
      private final boolean success;
      private final String message;
      private final T data;

      public static <T> ApiResponse<T> ok(T data) {
          return new ApiResponse<>(true, "OK", data);
      }

      public static <T> ApiResponse<T> error(String message) {
          return new ApiResponse<>(false, message, null);
      }
  }
  ```

## 2. HTTP 상태코드 가이드
| 상황 | 상태코드 |
|------|----------|
| 조회 성공 | 200 OK |
| 생성 성공 | 201 Created |
| 요청 오류 (입력값 검증 실패) | 400 Bad Request |
| 인증 실패 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 서버 오류 | 500 Internal Server Error |

## 3. REST API URL 설계
- URL은 명사(복수형)를 사용하고, 동사를 포함하지 않는다.
  ```
  GET    /fairytales           # 목록 조회
  GET    /fairytales/{id}      # 단건 조회
  POST   /fairytales           # 생성
  PUT    /fairytales/{id}      # 전체 수정
  PATCH  /fairytales/{id}      # 부분 수정
  DELETE /fairytales/{id}      # 삭제
  ```
- 특수 액션은 서브 리소스로 표현한다: `POST /fairytales/{id}/share`
- URL은 소문자 kebab-case를 사용한다: `/user-profiles`, `/character-parts`

## 4. DTO 스타일
- Request DTO: `{기능}Request.java` (예: `FairytaleGenerateRequest`)
- Response DTO: `{기능}Response.java` (예: `FairytaleResponse`)
- `@Builder`를 활용하여 생성한다.
- Bean Validation 어노테이션은 Request DTO에 적용한다.
  ```java
  @Getter
  @NoArgsConstructor
  public class FairytaleGenerateRequest {
      @NotBlank(message = "카테고리는 필수입니다.")
      private String category;

      @NotNull
      private Long characterId;
  }
  ```

## 5. 예외 응답 형식
- 예외 발생 시 응답은 `ApiResponse.error(message)` 형식으로 통일한다.
- 커스텀 예외는 `global/exception/` 하위에 도메인별로 정의한다.
  ```java
  // 예시: global/exception/FairytaleNotFoundException.java
  public class FairytaleNotFoundException extends RuntimeException {
      public FairytaleNotFoundException(Long id) {
          super("동화를 찾을 수 없습니다. id=" + id);
      }
  }
  ```

## 6. 로깅 스타일
- SLF4J + Logback을 사용한다 (`@Slf4j` Lombok 어노테이션 활용).
- `System.out.println()` 사용을 금지한다.
- 로그 레벨 가이드:
  | 레벨 | 용도 |
  |------|------|
  | ERROR | 예외·장애 발생 |
  | WARN  | 잠재적 문제 |
  | INFO  | 주요 비즈니스 이벤트 |
  | DEBUG | 개발 디버깅용 (운영 비활성화) |

## 7. 설정 파일 스타일 (YAML)
- 인덴트는 2칸 스페이스를 사용한다.
- 환경별 설정은 프로파일로 분리한다 (`application-{profile}.yaml`).
- 주석을 활용해 각 설정 항목의 목적을 명시한다.

## 8. Gradle 빌드 스크립트
- 의존성은 용도별로 그룹화하여 주석을 달아 관리한다.
  ```groovy
  dependencies {
      // Spring Boot Core
      implementation 'org.springframework.boot:spring-boot-starter-web'
      implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

      // Security
      implementation 'org.springframework.boot:spring-boot-starter-security'

      // Database
      runtimeOnly 'org.postgresql:postgresql'

      // Utility
      compileOnly 'org.projectlombok:lombok'
      annotationProcessor 'org.projectlombok:lombok'

      // Test
      testImplementation 'org.springframework.boot:spring-boot-starter-test'
  }
  ```

## 9. 코드 포맷
- Google Java Style Guide 또는 IntelliJ 기본 Java 포매터를 사용한다.
- 한 줄 최대 길이: 120자.
- 저장 시 자동 포맷을 IDE에서 활성화한다.
