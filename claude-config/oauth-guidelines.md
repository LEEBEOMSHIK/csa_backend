# OAuth 가이드라인

## 1. 기본 명세

### 제공자별 지원 현황

| 제공자 | 국가 | 프로토콜 | Spring Security 내장 지원 |
|--------|------|----------|--------------------------|
| Google | 한국·일본 | OAuth 2.0 + OIDC | O (built-in) |
| Naver | 한국 | OAuth 2.0 (비표준) | X (커스텀 필요) |
| Apple | 한국·일본 | OAuth 2.0 + OIDC | △ (부분 지원) |
| Yahoo Japan | 일본 | OAuth 2.0 + OIDC (YConnect v2) | △ (커스텀 등록 필요) |

### 핵심 전략: OAuth + JWT 조합

- OAuth는 **외부 제공자를 통한 신원 확인** 수단
- JWT는 **우리 서버의 세션 토큰** 수단
- 클라이언트(Flutter)가 OAuth 코드를 받아 백엔드에 전달 → 백엔드가 자체 JWT 발급
- 클라이언트는 이후 모든 API 호출에 우리 JWT만 사용 (제공자 토큰 직접 사용 금지)

### build.gradle 추가 의존성

```groovy
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
// Naver/Apple 커스텀 처리용
implementation 'org.springframework.boot:spring-boot-starter-webflux'  // WebClient
```

---

## 2. 주의사항

### 공통

- Client Secret은 절대 소스코드에 하드코딩 금지 → 환경변수 주입
- 제공자별 Client ID / Secret은 프로파일별로 분리 (local 테스트용 / prod 운영용)
- state 파라미터 검증 필수 (CSRF 방지)
- HTTPS 강제 (prod) — HTTP로 OAuth 콜백 시 토큰 탈취 위험

### Naver 전용

- 표준 OIDC 미지원 → id_token 없음, user_info API 별도 호출 필요
- 이메일은 사용자가 비공개 설정 가능 → nullable로 처리

### Apple 전용

- 최초 로그인 시에만 사용자 이름 반환 → 첫 응답에서 반드시 DB 저장
- Private Email Relay: 실제 이메일 대신 `@privaterelay.appleid.com` 이메일 반환 가능
- id_token은 Apple 공개키로 검증 (RS256) → `spring-security-oauth2-jose` 사용

### Yahoo Japan 전용

- YConnect v2 사용 (https://auth.login.yahoo.co.jp/yconnect/v2/authorization)
- `openid` scope 필수, `email` scope 별도 신청 필요
- 일본 법인 계약(Yahoo! JAPAN) 필요

---

## 3. OAuth 플로우 상세

### Flutter 앱 기준 Authorization Code Flow

```
[Flutter App]
    │
    ├─1. 제공자 OAuth URL로 이동 (flutter_web_auth 등)
    │    └─ google: https://accounts.google.com/o/oauth2/v2/auth
    │    └─ naver:  https://nid.naver.com/oauth2.0/authorize
    │    └─ apple:  https://appleid.apple.com/auth/authorize
    │    └─ yahoo:  https://auth.login.yahoo.co.jp/yconnect/v2/authorization
    │
    ├─2. 사용자 로그인 + 동의
    │
    ├─3. 제공자가 authorization_code 반환 (redirect_uri로)
    │
    ├─4. Flutter → 백엔드 POST /auth/oauth/{provider}
    │         body: { code, state, locale }
    │
[Backend]
    │
    ├─5. state 검증
    │
    ├─6. code → provider token 교환 (백엔드에서 직접)
    │    └─ POST https://{provider}/token
    │         (client_id, client_secret, code, redirect_uri)
    │
    ├─7. provider token으로 사용자 정보 조회
    │    └─ Google/Yahoo/Apple: id_token 파싱 (JWT 검증)
    │    └─ Naver: GET https://openapi.naver.com/v1/nid/me
    │
    ├─8. DB에서 사용자 조회 (provider + providerId 기준)
    │    └─ 신규: User 생성 (회원가입 처리)
    │    └─ 기존: 사용자 정보 업데이트 (이름 등)
    │
    ├─9. 자체 JWT 발급 (AccessToken 15분 + RefreshToken 7일)
    │
    └─10. Flutter에 JWT 반환
          { accessToken, refreshToken, isNewUser }

[Flutter App]
    └─ 이후 모든 API 요청: Authorization: Bearer {accessToken}
```

---

## 4. 구현 예시

### application.yaml OAuth 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
          apple:
            client-id: ${APPLE_CLIENT_ID}
            client-secret: ${APPLE_CLIENT_SECRET}
            scope: openid, email, name
            authorization-grant-type: authorization_code
          yahoo-japan:
            client-id: ${YAHOO_JP_CLIENT_ID}
            client-secret: ${YAHOO_JP_CLIENT_SECRET}
            scope: openid, email, profile
            authorization-grant-type: authorization_code
            # Naver는 표준 registration 불가 → 코드로 처리
        provider:
          yahoo-japan:
            authorization-uri: https://auth.login.yahoo.co.jp/yconnect/v2/authorization
            token-uri: https://auth.login.yahoo.co.jp/yconnect/v2/token
            user-info-uri: https://userinfo.yahooapis.jp/yconnect/v2/attribute
            user-name-attribute: sub
```

### OAuthUserInfo 공통 인터페이스

```java
public interface OAuthUserInfo {
    String getProvider();      // "google", "naver", "apple", "yahoo_japan"
    String getProviderId();    // 제공자 고유 사용자 ID
    String getEmail();         // nullable (Naver 비공개, Apple relay)
    String getName();
}
```

### OAuthService 핵심 구조

```java
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final NaverOAuthClient naverOAuthClient;   // 커스텀
    private final GoogleOAuthClient googleOAuthClient; // WebClient 기반

    public AuthResponse processOAuth(String provider, String code, String locale) {
        OAuthUserInfo userInfo = switch (provider) {
            case "google"      -> googleOAuthClient.getUserInfo(code);
            case "naver"       -> naverOAuthClient.getUserInfo(code);
            case "apple"       -> appleOAuthClient.getUserInfo(code);
            case "yahoo_japan" -> yahooJapanOAuthClient.getUserInfo(code);
            default            -> throw new IllegalArgumentException("지원하지 않는 제공자: " + provider);
        };

        User user = userRepository
            .findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
            .orElseGet(() -> createUser(userInfo, locale));

        String accessToken  = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        // RefreshToken DB 저장 (jwt-spec.md Rotation 전략)

        return new AuthResponse(accessToken, refreshToken);
    }
}
```

### Naver 커스텀 OAuth 클라이언트

```java
@Component
@RequiredArgsConstructor
public class NaverOAuthClient {

    private final WebClient webClient;

    @Value("${oauth.naver.client-id}")    private String clientId;
    @Value("${oauth.naver.client-secret}") private String clientSecret;

    public OAuthUserInfo getUserInfo(String code) {
        // 1. code → access_token
        NaverTokenResponse tokenResponse = webClient.post()
            .uri("https://nid.naver.com/oauth2.0/token")
            .bodyValue(Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
            ))
            .retrieve()
            .bodyToMono(NaverTokenResponse.class)
            .block();

        // 2. access_token → user info
        NaverUserResponse userResponse = webClient.get()
            .uri("https://openapi.naver.com/v1/nid/me")
            .header("Authorization", "Bearer " + tokenResponse.accessToken())
            .retrieve()
            .bodyToMono(NaverUserResponse.class)
            .block();

        return new NaverUserInfo(userResponse);
    }
}
```

### AuthController

```java
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<AuthResponse>> oauthLogin(
            @PathVariable String provider,
            @RequestBody OAuthRequest request) {
        AuthResponse response = oAuthService.processOAuth(
            provider, request.code(), request.locale());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

## 5. DB 연동

### User 엔티티 (OAuth 전용)

```java
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@Getter
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;     // "google" | "naver" | "apple" | "yahoo_japan"

    @Column(nullable = false)
    private String providerId;   // 제공자 고유 ID

    @Column                      // nullable: Naver 비공개, Apple relay
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String locale;       // "ko" | "ja"

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### 조회 전략

- 로그인 시 `provider` + `providerId` 조합으로 사용자 식별
- 이메일은 식별자로 사용하지 않음 (제공자마다 이메일 형태 다름, Apple relay 문제)
- 신규 사용자 → isNewUser: true 반환하여 Flutter에서 온보딩 처리 가능

### RefreshToken 테이블 (jwt-spec.md 참조)

```sql
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR NOT NULL,   -- BCrypt 해시
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 6. JWT와의 통합 분석

### 역할 분리

| 역할 | 담당 |
|------|------|
| 신원 확인 (누구인지) | OAuth 제공자 |
| API 인증 (토큰) | 자체 JWT (jwt-spec.md) |
| 세션 유지 | Refresh Token (DB 저장) |

### 통합 포인트

1. OAuth 콜백 처리 후 즉시 JWT 발급 → 이후 OAuth 제공자와 통신 없음
2. JWT Payload에 `provider` 클레임 포함 권장 (디버깅·분석 목적)
3. Refresh Token Rotation은 jwt-spec.md와 동일한 Sliding Window 전략 적용
4. 제공자 Access Token은 DB에 저장하지 않음 (불필요, 보안 리스크)

### 확장 고려

- 한 계정에 여러 제공자 연결(계정 연동) 필요 시:
  - `user_oauth_providers` 테이블 별도 분리
  - 이메일 기준으로 동일 사용자 판단 (단, Apple relay 예외 처리 필요)

---

## 7. 환경변수 정리

| 변수명 | 설명 | 적용 프로파일 |
|--------|------|--------------|
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | dev, prod |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | dev, prod |
| `NAVER_CLIENT_ID` | Naver OAuth Client ID | dev, prod |
| `NAVER_CLIENT_SECRET` | Naver OAuth Client Secret | dev, prod |
| `APPLE_CLIENT_ID` | Apple Service ID | dev, prod |
| `APPLE_CLIENT_SECRET` | Apple JWT 기반 Secret | dev, prod |
| `YAHOO_JP_CLIENT_ID` | Yahoo Japan Client ID | dev, prod |
| `YAHOO_JP_CLIENT_SECRET` | Yahoo Japan Client Secret | dev, prod |

local 프로파일: `application-local.yaml`에 테스트용 값 허용 (`.gitignore` 필수)
