# 즐겨찾기 — 사용자 식별 및 DB 저장 흐름 분석 보고서

## 1. 전체 흐름 요약

```
[Flutter 앱]                    [Spring Boot 백엔드]
  │                                     │
  ├─ Google 로그인 버튼 탭               │
  ├─ GoogleSignIn.signIn()              │
  ├─ Google AccessToken 획득             │
  ├─ POST /auth/oauth/google ──────────►│
  │   { accessToken, locale }           ├─ Google API로 사용자 정보 조회
  │                                     ├─ users 테이블 조회 (provider + providerId)
  │                                     ├─ 없으면 신규 User 저장
  │                                     ├─ JWT accessToken + refreshToken 발급
  │◄─────────────────── TokenResponse ──┤
  ├─ FlutterSecureStorage에 토큰 저장    │
  │                                     │
  ├─ (이후 모든 요청)                     │
  ├─ Authorization: Bearer {accessToken} │
  │                                     ├─ JwtAuthenticationFilter
  │                                     ├─ JWT 검증 → userId 추출
  │                                     └─ SecurityContext에 인증 정보 세팅
```

---

## 2. Google OAuth → 사용자 식별 상세 흐름

### Step 1 — 프론트엔드: Google 로그인
**파일:** `csa_frontend/lib/features/auth/services/google_auth_service.dart`

```dart
final account = await _googleSignIn.signIn();
final auth = await account.authentication;
final accessToken = auth.accessToken;  // ← Google Access Token 획득

await ApiClient.instance.post('/auth/oauth/google', data: {
  'accessToken': accessToken,
  'locale': locale,  // 앱 현재 언어 (ko / ja)
});
```

Google SDK에서 받은 **Access Token**을 백엔드로 전달합니다.

---

### Step 2 — 백엔드: Google API로 사용자 정보 검증
**파일:** `src/main/java/org/example/csa_backend/auth/oauth/GoogleOAuthClient.java`

```java
// GET https://www.googleapis.com/oauth2/v3/userinfo
// Authorization: Bearer {google_access_token}
return new GoogleUserInfo(
    response.get("sub"),    // ← Google 고유 사용자 ID (불변)
    response.get("email"),
    response.get("name")
);
```

Google의 공식 userinfo 엔드포인트로 **`sub` (Subject ID)**를 가져옵니다. `sub`는 Google 계정마다 고유하며 절대 변경되지 않는 값입니다.

---

### Step 3 — 백엔드: DB에서 사용자 조회/신규 생성
**파일:** `src/main/java/org/example/csa_backend/auth/oauth/OAuthService.java`

```java
User user = userRepository
    .findByProviderAndProviderId("google", userInfo.sub())
    .orElseGet(() -> userRepository.save(
        new User(userInfo.email(), "google", userInfo.sub(), userInfo.name(), request.locale())
    ));
```

| 컬럼 | 값 | 역할 |
|---|---|---|
| `provider` | `"google"` | OAuth 제공자 식별 |
| `provider_id` | Google의 `sub` | **사용자 고유 식별자** |
| `email` | Google 이메일 | 보조 식별자 |
| `name` | Google 이름 | 표시용 |
| `locale` | `"ko"` / `"ja"` | 앱 언어 |

- **이미 가입한 사용자** → DB에서 조회 후 기존 User 반환
- **신규 사용자** → `users` 테이블에 INSERT 후 반환

> `users` 테이블의 복합 유니크 제약: `(provider, provider_id)` — 중복 저장 방지

---

### Step 4 — 백엔드: JWT 발급
**파일:** `src/main/java/org/example/csa_backend/jwt/JwtProvider.java`

```java
// Access Token: userId + email 포함, 만료시간 설정
String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
// Refresh Token: userId만 포함
String rawRefreshToken = jwtProvider.generateRefreshToken(user.getId());
```

JWT payload (Access Token):
```json
{
  "sub": "42",          // ← users.id (Long, PK)
  "email": "user@gmail.com",
  "iat": 1711000000,
  "exp": 1711003600
}
```

Refresh Token은 **SHA-256 해시**로 변환 후 `refresh_tokens` 테이블에 저장됩니다.

---

### Step 5 — 프론트엔드: 토큰 안전 저장
**파일:** `csa_frontend/lib/features/auth/services/google_auth_service.dart`

```dart
await _storage.write(key: 'access_token', value: response['accessToken']);
await _storage.write(key: 'refresh_token', value: response['refreshToken']);
```

`FlutterSecureStorage` (Android: Keystore, iOS: Keychain)에 암호화 저장합니다.

---

## 3. 이후 API 요청에서 사용자 식별 흐름

### 프론트엔드: 자동 토큰 주입
**파일:** `csa_frontend/lib/shared/services/api_client.dart` — `_AuthInterceptor`

```dart
// 모든 요청에 자동으로 헤더 추가
final token = await _storage.read(key: 'access_token');
options.headers['Authorization'] = 'Bearer $token';
```

모든 API 요청에 Bearer 토큰이 자동 삽입됩니다.

---

### 백엔드: JWT 필터로 사용자 복원
**파일:** `src/main/java/org/example/csa_backend/jwt/JwtAuthenticationFilter.java`

```java
String token = resolveToken(request);  // Authorization 헤더에서 추출
Claims claims = jwtProvider.extractClaims(token);
String userId = claims.getSubject();   // ← "42" (users.id)
UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
SecurityContextHolder.getContext().setAuthentication(auth);
```

**요청마다** JWT에서 `userId`를 추출해 Spring Security의 인증 컨텍스트에 세팅합니다. 별도 세션 없이 **Stateless** 방식입니다.

---

### 컨트롤러에서 사용자 ID 꺼내기
**파일:** `src/main/java/org/example/csa_backend/user/UserController.java`

```java
@GetMapping("/me")
public ResponseEntity<?> me(Authentication authentication) {
    Long userId = Long.parseLong(authentication.getName()); // ← JWT subject
    User user = userRepository.findById(userId).orElseThrow();
    ...
}
```

`authentication.getName()` = JWT의 `sub` = `users.id` (Long)

---

## 4. 즐겨찾기 현재 상태 및 문제점

**파일:** `csa_frontend/lib/utils/locale_provider.dart`, `csa_frontend/lib/features/favorites/screens/favorites_screen.dart`

```dart
// 전역 인메모리 상태
final favoritesNotifier = ValueNotifier<List<FairytaleItem>>([]);
```

**현재 즐겨찾기는 메모리에만 존재합니다:**

| 항목 | 현재 상태 |
|---|---|
| 저장 위치 | 앱 메모리 (ValueNotifier) |
| 백엔드 API | **없음** |
| DB 테이블 | **없음** |
| 앱 재시작 시 | **데이터 소멸** |
| 로그인 사용자 연결 | **없음** |

즐겨찾기 추가/삭제는 순수 프론트엔드 상태 변경만 일어납니다:

```dart
// 삭제 시 (favorites_screen.dart:171)
current.removeWhere((f) => f.id == item.id);
favoritesNotifier.value = current;
```

---

## 5. 즐겨찾기 DB 저장 구현 방법 (현재 누락된 부분)

현재 코드를 기반으로 즐겨찾기를 DB에 저장하려면 아래 작업이 필요합니다.

### 백엔드 — 추가 필요 사항

```
1. favorites 테이블 생성
   ├─ id (PK)
   ├─ user_id (FK → users.id)
   ├─ fairytale_id (FK → fairytales.id)
   └─ created_at

2. Favorite Entity / Repository 생성
3. FavoriteController 생성
   ├─ POST   /favorites/{fairytaleId}   → 추가
   ├─ DELETE /favorites/{fairytaleId}   → 삭제
   └─ GET    /favorites                 → 목록 조회
4. 컨트롤러에서 Authentication으로 userId 추출
   Long userId = Long.parseLong(authentication.getName());
```

### 프론트엔드 — 변경 필요 사항

```
1. FavoriteService 추가
   ApiClient.instance.post('/favorites/$fairytaleId')
   ApiClient.instance.delete('/favorites/$fairytaleId')

2. 앱 시작 시 서버에서 즐겨찾기 목록 fetch
3. favoritesNotifier를 서버 상태와 동기화
```

---

## 6. 핵심 정리

| 단계 | 사용자 식별 방법 |
|---|---|
| 로그인 | Google `sub` (고유 ID) + `provider="google"` |
| 신규 가입 | `users` 테이블에 INSERT |
| JWT 발급 | `sub` = `users.id` (내부 PK) |
| API 요청 | Authorization: Bearer 헤더 → JWT 파싱 → `userId` |
| 컨트롤러 | `authentication.getName()` → `userId` |
| 즐겨찾기 연결 | `user_id` FK로 연결 (**미구현 상태**) |

**결론:** 사용자 식별 인프라(OAuth → JWT → SecurityContext)는 완성되어 있지만, 즐겨찾기는 아직 인메모리 상태이며 백엔드 API와 DB 테이블이 전혀 구현되어 있지 않습니다. `authentication.getName()`으로 `userId`를 뽑는 패턴은 `UserController`에 이미 존재하므로, 이를 그대로 복사해 `FavoriteController`를 만들면 됩니다.
