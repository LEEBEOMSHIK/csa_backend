# 설정 및 약관 관리 — Claude 작업 지침

## 개요
마이페이지(설정 화면)의 앱 설정과 약관 동의 데이터를 관리하는 도메인 설계 기준이다.
설정 관련 코드 작업 시 반드시 이 문서를 참조한다.

---

## 테이블 구조

### user_settings (앱 설정, users와 1:1)
```
id              BIGINT PK
user_id         BIGINT FK(users.id) UNIQUE
locale          VARCHAR(5)   — "ko" | "ja"
text_noti_yn    CHAR(1)      — "Y" | "N"
push_noti_yn    CHAR(1)      — "Y" | "N"
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### term_agreements (약관 동의 이력, users와 1:N)
```
id              BIGINT PK
user_id         BIGINT FK(users.id)
term_type       VARCHAR(20)  — "SERVICE" | "FINANCE" | "PRIVACY"
term_version    VARCHAR(10)  — e.g., "v1.0"
agreed_at       TIMESTAMP
UNIQUE(user_id, term_type, term_version)
```

---

## 설계 규칙

1. **users 테이블에 설정 컬럼을 추가하지 않는다.**
   → 인증 도메인과 설정 도메인을 분리하기 위해 `user_settings` 별도 테이블 사용

2. **boolean 컬럼은 CHAR(1) "Y"/"N"으로 표현한다.**
   → 프로젝트 전체 컨벤션 (BaseEntity.delYn 패턴과 동일)

3. **약관 동의는 UPDATE하지 않고 INSERT로 이력을 쌓는다.**
   → 약관 동의 이력은 법적 보관 의무가 있음

4. **신규 User 생성 시 user_settings도 함께 생성한다.**
   → OAuthService.processOAuth()에서 User INSERT 직후 UserSettings INSERT

5. **term_type은 "SERVICE" | "FINANCE" | "PRIVACY" 세 가지만 허용한다.**
   → 앱 약관 3종과 1:1 대응

---

## API 엔드포인트

```
GET  /users/settings           설정 조회 (인증 필요)
PUT  /users/settings           설정 변경 (locale, textNotiEnabled, pushNotiEnabled)
GET  /users/terms              동의 약관 목록 조회 (인증 필요)
POST /users/terms/{type}       약관 동의 기록 (type: service | finance | privacy)
```

- 모든 엔드포인트는 JWT 인증 필수 (`anyRequest().authenticated()` 범위)
- SecurityConfig 수정 불필요

---

## 패키지 위치

```
org.example.csa_backend/
└── setting/                   ← 신규 패키지
    ├── UserSettings.java
    ├── UserSettingsRepository.java
    ├── UserSettingsService.java
    ├── TermAgreement.java
    ├── TermAgreementRepository.java
    ├── TermAgreementService.java
    ├── UserSettingsController.java
    └── dto/
        ├── UserSettingsDto.java
        ├── UpdateSettingsRequest.java
        ├── TermAgreementDto.java
        └── AgreeTermRequest.java
```

---

## 프론트엔드 연동 패턴

- **설정 로드:** `MainScreen.initState()`에서 favorites와 동일하게 fetch
- **설정 저장:** 변경 즉시 `PUT /users/settings` 호출 (낙관적 업데이트 없이 응답 후 반영)
- **약관 동의:** 최초 1회만 `POST /users/terms/{type}` 호출
- **서비스 파일 위치:** `csa_frontend/lib/features/my/services/user_settings_service.dart`

---

## 참고 파일
- 분석 보고서 전문: `csa_backend/setting-manage-strategy.md`
- 관련 UI: `csa_frontend/lib/features/my/screens/my_screen.dart`
- 연동 패턴 참고: `csa_backend/favorite/` 패키지 (동일 구조)

# 설정 및 약관 데이터 관리 전략 보고서

## 1. 현재 상태 (As-Is) 분석

### 프론트엔드 — my_screen.dart

| 설정 항목 | 현재 저장 방식 | 문제점 |
|---|---|---|
| 언어 설정 (ko/ja) | `localeNotifier` (인메모리) | 앱 재시작 시 초기화, 서버 미반영 |
| 문자 알림 토글 | `_textNotiEnabled` (로컬 State) | 앱 재시작 시 초기화, 서버 미저장 |
| 푸시 알림 토글 | `_pushNotiEnabled` (로컬 State) | 앱 재시작 시 초기화, 서버 미저장 |
| 약관 3종 링크 | 탭 이벤트 없음 (dead link) | 동의 기록 없음, 법적 리스크 존재 |
| 구매 내역 | 미구현 | - |
| 찜 목록 내역 | favorites 테이블 구현 완료 | 정상 |

**약관 3종 현황 (settingsPolicy 섹션):**
- 서비스 이용약관 (`settingsTerms`)
- 전자금융거래 이용약관 (`settingsFinanceTerms`)
- 개인정보처리방침 (`settingsPrivacy`)

→ UI에 항목은 존재하나 모두 onTap 없는 dead link 상태

### 백엔드 — 현재 없는 것

- 설정 관련 테이블/엔티티 **전무**
- 약관 동의 관련 테이블/엔티티 **전무**
- `users.locale` 컬럼은 존재하나 OAuth 최초 가입 시 1회만 저장되며, 이후 업데이트 API 없음
- 알림 설정 저장 API 없음

---

## 2. 데이터 분류 및 저장 전략

### 분류 원칙

설정 데이터는 성격에 따라 두 그룹으로 분리한다.

| 그룹 | 데이터 | 특성 |
|---|---|---|
| **앱 설정** | 언어, 알림 | 최신 값만 필요, 1:1 관계 |
| **약관 동의** | 3종 × 버전별 | 이력 보관 필요, 1:N 관계 |

### 왜 `users` 컬럼 추가가 아닌 별도 테이블인가

`users` 테이블은 **인증(Authentication) 도메인**이다. 여기에 설정 컬럼을 추가하면:
- 인증 도메인과 설정 도메인이 혼합 → 단일책임원칙(SRP) 위반
- 설정 항목이 늘어날수록 `users` 테이블이 비대해짐
- 설정만 조회·수정하는 쿼리에도 불필요한 인증 데이터가 포함됨

→ **별도 테이블로 도메인 분리**를 선택한다.

---

## 3. 테이블 설계 (To-Be)

### user_settings 테이블

```
user_settings
├── id              BIGINT, PK, AUTO_INCREMENT
├── user_id         BIGINT, FK → users.id, UNIQUE (1:1)
├── locale          VARCHAR(5)  — "ko" | "ja", default "ko"
├── text_noti_yn    CHAR(1)     — "Y" | "N", default "Y"
├── push_noti_yn    CHAR(1)     — "Y" | "N", default "Y"
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP
```

**생성 시점:** OAuthService에서 신규 User INSERT 직후 함께 INSERT
**갱신:** `PUT /users/settings` 호출 시 UPDATE

> 기존 프로젝트 컨벤션에 따라 boolean 대신 `CHAR(1) "Y"/"N"` 사용
> (BaseEntity의 DEL_YN 패턴과 일치)

---

### term_agreements 테이블

```
term_agreements
├── id              BIGINT, PK, AUTO_INCREMENT
├── user_id         BIGINT, FK → users.id
├── term_type       VARCHAR(20) — "SERVICE" | "FINANCE" | "PRIVACY"
├── term_version    VARCHAR(10) — e.g., "v1.0"
├── agreed_at       TIMESTAMP
└── UNIQUE(user_id, term_type, term_version)
```

**저장 방식:** 약관 버전이 올라갈 때 새 행 INSERT (이력 보관)
**최신 동의 조회:** `term_type`별 `MAX(agreed_at)` 또는 최신 버전 행

> 법적으로 약관 동의 이력은 보관 의무가 있으므로 UPDATE 대신 INSERT 방식 사용

---

## 4. API 설계 (To-Be)

```
GET  /users/settings           현재 설정 전체 조회 (locale, 알림 2종)
PUT  /users/settings           언어·알림 설정 갱신
GET  /users/terms              동의한 약관 목록 조회 (type별 최신 agreed_at)
POST /users/terms/{type}       약관 동의 기록 (type: service | finance | privacy)
```

### 요청/응답 예시

**GET /users/settings**
```json
{
  "locale": "ko",
  "textNotiEnabled": true,
  "pushNotiEnabled": false
}
```

**PUT /users/settings**
```json
// 요청 body
{ "locale": "ja", "textNotiEnabled": true, "pushNotiEnabled": false }
// 응답: 200 OK (변경된 설정 반환)
```

**GET /users/terms**
```json
[
  { "termType": "SERVICE",  "termVersion": "v1.0", "agreedAt": "2026-03-25T10:00:00" },
  { "termType": "PRIVACY",  "termVersion": "v1.0", "agreedAt": "2026-03-25T10:00:00" }
]
```

**POST /users/terms/service**
```json
// 요청 body
{ "termVersion": "v1.0" }
// 응답: 200 OK
```

---

## 5. 프론트엔드 연동 전략

### 설정 로드 (앱 시작 시)

`main_screen.dart`의 `initState()`에서 favorites 로드와 함께 설정도 fetch:
```dart
Future<void> _loadSettings() async {
  try {
    final settings = await UserSettingsService.instance.fetchSettings();
    localeNotifier.value = Locale(settings.locale);
    // 알림 상태도 전역 notifier로 관리
  } catch (_) {}
}
```

### 설정 변경 시 즉시 서버 반영
- 언어 변경: `_showLanguagePicker()` 내 언어 선택 직후 `PUT /users/settings` 호출
- 알림 토글: `_ToggleRow.onChanged` 콜백에서 `PUT /users/settings` 호출

### 약관 처리
- 약관 링크 탭 시: 약관 내용 화면(WebView 또는 정적 화면)으로 이동
- 최초 동의 시: `POST /users/terms/{type}` 호출 후 동의 상태 업데이트

---

## 6. 구현 순서 권장

```
[백엔드]
1. UserSettings Entity + Repository 생성
2. TermAgreement Entity + Repository 생성
3. UserSettingsService (CRUD 로직)
4. TermAgreementService (동의 기록·조회 로직)
5. UserController에 /users/settings, /users/terms 엔드포인트 추가
6. OAuthService.processOAuth()에서 신규 유저 생성 시 UserSettings 함께 생성

[프론트엔드]
1. UserSettingsService 생성 (API 클라이언트)
2. main_screen.dart initState()에 설정 fetch 추가
3. my_screen.dart 언어 변경 시 API 호출 연동
4. my_screen.dart 알림 토글 변경 시 API 호출 연동
5. 약관 링크에 navigation + 동의 API 호출 연동
```

---

## 7. 핵심 요약

| 항목 | 전략 |
|---|---|
| 언어 설정 | `user_settings.locale` — 서버 저장, 앱 시작 시 fetch |
| 알림 설정 | `user_settings.text_noti_yn / push_noti_yn` — 서버 저장 |
| 약관 동의 | `term_agreements` — INSERT 이력 보관 방식 |
| 설정 로드 시점 | `MainScreen.initState()` (favorites와 동일 패턴) |
| 설정 저장 시점 | 변경 즉시 API 호출 (낙관적 업데이트 없이 응답 후 반영) |
| 도메인 분리 | `users` 컬럼 추가 대신 별도 테이블로 SRP 준수 |
