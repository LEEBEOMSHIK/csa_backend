# jwt-spec.md — JWT 설계 명세

## 1. 기본 명세

### 라이브러리 선택
- **jjwt 0.12.x** (`io.jsonwebtoken`) 사용
- 선택 이유: Spring Boot 4 / Java 17 호환, 활발한 유지보수, 명시적 API
- 비교 대상: `spring-security-oauth2-jose` — 복잡도 높음, OAuth2 전용 구조에 더 적합

### 토큰 구조

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 만료 | **15분** (prod 기준) | **7일** |
| 저장 | 클라이언트 메모리 | DB (해시값 저장) |
| 용도 | API 인증 | Access Token 재발급 |

### 알고리즘
- **HS256** (HMAC-SHA256)
- 단일 서버 구조에 적합, 대칭키 방식

### 전달 방식
- 요청 헤더: `Authorization: Bearer <token>`
- Stateless 인증 (서버 세션 없음)

---

## 2. 주의사항

- **Secret Key**: 256bit 이상 랜덤값, 환경변수 `JWT_SECRET`으로 주입, 코드 하드코딩 절대 금지
- **Refresh Token**: DB에 BCrypt 해시값으로 저장, Rotation 시 기존 토큰 즉시 무효화
- **예외 처리**: `ExpiredJwtException`, `MalformedJwtException` → HTTP 401 응답 (500 반환 금지)
- **HTTPS**: prod 프로파일에서 강제 적용
- **Payload 제한**: Access Token에 비밀번호·주민번호 등 민감 정보 포함 금지

---

## 3. 구현 순서

1. User 엔티티 + Repository 구현
2. JWT 의존성 추가 (`build.gradle`)
3. `JwtProvider` 구현
4. `JwtAuthenticationFilter` 구현
5. `SecurityConfig` 설정
6. `AuthController` 구현 (`/auth/signup`, `/auth/login`, `/auth/refresh`)
7. `/users/me` 엔드포인트 구현

---

## 4. 구현 예시

### build.gradle 의존성

```groovy
dependencies {
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

### JwtProvider 핵심 코드

```java
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateAccessToken(Long userId, String email) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException | MalformedJwtException | SecurityException e) {
            return false;
        }
    }
}
```

### JwtAuthenticationFilter 코드 구조

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.extractClaims(token);
            String userId = claims.getSubject();
            UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

### SecurityConfig filterChain 설정

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}
```

### application.yaml JWT 설정 항목

```yaml
jwt:
  secret: ${JWT_SECRET}
  access-expiration: 900000       # 15분 (ms)
  refresh-expiration: 604800000   # 7일 (ms)
```

---

## 5. Key 관리 방향

### 프로파일별 전략

| 프로파일 | 방식 | 비고 |
|----------|------|------|
| `local` | `application-local.yaml`에 평문 테스트 키 허용 | 예시: `test-secret-key-for-local-dev-only-256bit!!` |
| `dev` | 환경변수 `JWT_SECRET` 주입 필수 | |
| `prod` | 환경변수 `JWT_SECRET` 주입 필수 | Secret Manager 연동 권장 |

> `application-local.yaml`은 `.gitignore`에 등록되어 있어야 한다.

### Refresh Token Rotation

- Sliding Window 방식: Refresh Token 사용 시마다 새 Refresh Token 발급
- 기존 Refresh Token은 즉시 DB에서 삭제(무효화)
- 탈취된 토큰 재사용 시 감지 가능 (이미 삭제된 토큰으로 요청 → 401)

### 향후 RS256 마이그레이션 고려 시점

- 다중 서버 구조 또는 마이크로서비스 전환 시 RS256(비대칭키)으로 마이그레이션
- 현재 단일 서버 구조에서는 HS256으로 충분
