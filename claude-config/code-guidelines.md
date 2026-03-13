# code-guidelines.md — 코드 기본 룰

## 1. 언어 및 버전
- **Java**: 17 (LTS)
- **Spring Boot**: 4.0.3
- **Gradle**: Kotlin DSL 또는 Groovy DSL

## 2. 패키지 및 디렉터리 구조
```
src/main/java/org/example/csa_backend/
├── domain/                    # 도메인별 패키지 (feature 기반)
│   ├── fairytale/
│   │   ├── controller/        # REST Controller
│   │   ├── service/           # 비즈니스 로직
│   │   ├── repository/        # JPA Repository
│   │   ├── dto/               # Request / Response DTO
│   │   └── entity/            # JPA Entity
│   ├── character/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── dto/
│   │   └── entity/
│   └── user/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── dto/
│       └── entity/
├── global/
│   ├── config/                # Spring 설정 클래스 (Security, CORS 등)
│   ├── exception/             # 전역 예외 처리 (GlobalExceptionHandler)
│   ├── response/              # 공통 API 응답 객체
│   └── util/                  # 유틸리티 클래스
└── CsaBackendApplication.java # 애플리케이션 진입점
```

## 3. 네이밍 컨벤션
| 대상 | 컨벤션 | 예시 |
|------|--------|------|
| 클래스 | PascalCase | `FairytaleService`, `UserController` |
| 메서드 / 변수 | camelCase | `generateFairytale()`, `userId` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 패키지 | 소문자, 언더스코어 없이 | `org.example.csabackend` |
| 파일 (비Java) | kebab-case | `application-local.yaml` |
| REST 엔드포인트 | kebab-case | `/fairytale/generate`, `/user-profile` |

## 4. Java 코딩 규칙
- 명시적 타입 선언을 우선한다 (`var`는 타입이 명백한 경우에만 허용).
- `null` 반환보다 `Optional<T>`를 활용한다.
- 불필요한 `static` 메서드 남용을 지양한다.
- `@RequiredArgsConstructor`를 활용한 생성자 주입을 기본으로 사용한다 — 필드 주입(`@Autowired`) 금지.
- Lombok: `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@ToString`, `@EqualsAndHashCode` 활용.

## 5. Controller 규칙
- `@RestController` + `@RequestMapping`으로 기본 경로를 명시한다.
- 비즈니스 로직을 Controller에 작성하지 않는다 — Service에 위임한다.
- 요청 파라미터에 `@Valid`를 반드시 적용한다.
- 응답은 공통 응답 객체(`ApiResponse<T>`)로 통일한다.

## 6. Service 규칙
- `@Service` + `@Transactional`을 적절히 사용한다.
- 읽기 전용 메서드에는 `@Transactional(readOnly = true)`를 명시한다.
- 도메인 로직은 Entity 또는 도메인 객체 내부로 위임한다.
- 하나의 Service 메서드는 하나의 책임만 가진다.

## 7. Repository 규칙
- `JpaRepository<Entity, ID>`를 기본으로 사용한다.
- 복잡한 쿼리는 `@Query` JPQL 또는 QueryDSL을 사용한다.
- 네이티브 쿼리는 최소화하고, 사용 시 파라미터 바인딩을 반드시 적용한다.

## 8. Entity 규칙
- `@Entity`, `@Table`을 명시하고 테이블명을 직접 지정한다.
- 기본 키는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용한다.
- 생성 일시·수정 일시는 `@CreatedDate`, `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`로 관리한다.
- 양방향 연관관계는 신중히 사용하고, 단방향을 우선 고려한다.

## 9. 에러 처리
- `@RestControllerAdvice`를 활용한 전역 예외 처리(`GlobalExceptionHandler`)를 사용한다.
- 커스텀 예외 클래스를 정의하고, HTTP 상태코드와 에러 메시지를 통일한다.
- 발생 가능성 없는 상황에 대한 방어 코드는 추가하지 않는다.

## 10. 테스트
- 비즈니스 로직(Service)은 단위 테스트를 작성한다 (`@ExtendWith(MockitoExtension.class)`).
- Controller 테스트는 `@WebMvcTest`를 사용한다.
- Repository 테스트는 `@DataJpaTest`를 사용한다.
- 테스트 파일은 `src/test/` 디렉터리에 소스 구조와 동일하게 구성한다.

## 11. 빌드 및 린트
- `./gradlew build` 에러가 없는 상태로 커밋한다.
- `./gradlew test` 전체 테스트가 통과한 상태로 커밋한다.
