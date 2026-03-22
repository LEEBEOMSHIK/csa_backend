# db-guidelines.md — 데이터베이스 기본 룰

## 1. DB 작업 공통 원칙

- 모든 DDL(테이블 생성·수정·삭제)은 마이그레이션 파일로 관리한다 (Flyway 또는 Liquibase).
- `ddl-auto: none` (prod) / `ddl-auto: update` (local·dev) — 운영 환경에서 자동 DDL 금지.
- 스키마 변경은 반드시 롤백 스크립트를 함께 준비한다.
- 직접 운영 DB에 DDL을 실행하지 않는다 — 반드시 마이그레이션 절차를 따른다.
- 모든 테이블·컬럼명은 **대문자 + 언더스코어**(`UPPER_SNAKE_CASE`)로 작성한다.

---

## 2. 테이블 생성 규칙

- 테이블명은 복수형 명사로 작성한다. (예: `USERS`, `FAIRYTALES`, `CHARACTERS`)
- 기본 키는 `ID` 컬럼으로 통일하며, `BIGSERIAL` (자동 증가)을 사용한다.
- 모든 테이블에는 아래 **공통 감사 필드(Audit Fields)** 를 반드시 포함한다.
- 외래 키는 명시적으로 선언하고, 참조 무결성을 보장한다.
- 인덱스는 조회 빈도가 높은 컬럼에만 추가한다 — 과도한 인덱스 금지.
- `NOT NULL` 제약은 기본값으로 적용하고, NULL 허용이 필요한 경우 명시적으로 표기한다.

### 공통 감사 필드 (모든 테이블 필수)

| 컬럼명 | 타입 | 설명 | 필수 여부 |
|--------|------|------|----------|
| `CRE_DT` | `TIMESTAMP` | 생성 일시 | NOT NULL |
| `CRE_ID` | `VARCHAR(50)` | 생성자 ID | NOT NULL |
| `MOD_DT` | `TIMESTAMP` | 수정 일시 | NULL 허용 |
| `MOD_ID` | `VARCHAR(50)` | 수정자 ID | NULL 허용 |
| `DEL_YN` | `CHAR(1)` | 삭제 여부 (`Y`/`N`) | NOT NULL, DEFAULT `N` |

### 테이블 생성 예시

```sql
CREATE TABLE USERS (
    ID        BIGSERIAL      PRIMARY KEY,
    EMAIL     VARCHAR(255)   NOT NULL UNIQUE,
    NICKNAME  VARCHAR(100)   NOT NULL,
    CRE_DT    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CRE_ID    VARCHAR(50)    NOT NULL,
    MOD_DT    TIMESTAMP,
    MOD_ID    VARCHAR(50),
    DEL_YN    CHAR(1)        NOT NULL DEFAULT 'N'
);
```

### JPA Entity 매핑 규칙

- `@CreatedDate` → `CRE_DT`
- `@CreatedBy` → `CRE_ID`
- `@LastModifiedDate` → `MOD_DT`
- `@LastModifiedBy` → `MOD_ID`
- `@EntityListeners(AuditingEntityListener.class)` 필수 적용.
- 공통 감사 필드는 `BaseEntity` 추상 클래스로 분리하여 상속한다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "CRE_DT", nullable = false, updatable = false)
    private LocalDateTime creDt;

    @CreatedBy
    @Column(name = "CRE_ID", nullable = false, updatable = false, length = 50)
    private String creId;

    @LastModifiedDate
    @Column(name = "MOD_DT")
    private LocalDateTime modDt;

    @LastModifiedBy
    @Column(name = "MOD_ID", length = 50)
    private String modId;

    @Column(name = "DEL_YN", nullable = false, length = 1)
    private String delYn = "N";
}
```

---

## 3. 테이블 수정 규칙

- 컬럼 추가는 허용하되, 기존 컬럼의 타입·이름 변경은 신중히 검토 후 진행한다.
- 컬럼 이름 변경 시 반드시 애플리케이션 코드(Entity 매핑)와 동시에 변경한다.
- `NOT NULL` 컬럼 추가 시 `DEFAULT` 값을 반드시 지정한다 — 기존 데이터 오류 방지.
- 인덱스 추가·삭제는 운영 트래픽이 낮은 시간대에 수행한다.
- 수정 이력은 마이그레이션 파일의 버전으로 관리한다.

---

## 4. 테이블 삭제 규칙

- **물리 삭제(DROP TABLE)는 원칙적으로 금지한다.**
- 더 이상 사용하지 않는 테이블은 `DEL_YN = 'Y'` 처리 후 일정 보존 기간(최소 30일) 후 삭제를 검토한다.
- 물리 삭제가 불가피한 경우 반드시 데이터 백업 후 진행한다.
- 데이터 삭제는 물리 삭제 대신 **소프트 삭제**(`DEL_YN = 'Y'` 업데이트)를 기본으로 한다.
- 소프트 삭제 적용 시 조회 쿼리에 `WHERE DEL_YN = 'N'` 조건을 반드시 포함한다.

---

## 5. 계정별 권한 규칙

### 계정 종류

| 계정 | 용도 | 허용 권한 |
|------|------|----------|
| `app_local` | 로컬 개발 | SELECT, INSERT, UPDATE, DELETE, DDL 전체 |
| `app_dev` | 개발 서버 앱 계정 | SELECT, INSERT, UPDATE, DELETE |
| `app_prod` | 운영 서버 앱 계정 | SELECT, INSERT, UPDATE, DELETE |
| `dba_dev` | 개발 DBA | SELECT, INSERT, UPDATE, DELETE, DDL 전체 |
| `dba_prod` | 운영 DBA | SELECT, INSERT, UPDATE, DELETE, DDL 전체 |
| `readonly` | 조회 전용 (모니터링·분석) | SELECT |

### 권한 원칙

- 애플리케이션 계정(`app_*`)에는 **DDL 권한을 부여하지 않는다** — DROP, CREATE, ALTER 금지.
- 운영 계정(`*_prod`)의 비밀번호는 개발 계정과 반드시 다르게 설정한다.
- 계정 비밀번호는 소스코드·설정 파일에 평문으로 저장하지 않는다 (운영 환경 기준).
- 권한은 최소 권한 원칙(Principle of Least Privilege)에 따라 필요한 것만 부여한다.
- `readonly` 계정은 SELECT 외 일체의 변경 권한을 부여하지 않는다.

### 권한 부여 예시

```sql
-- 앱 계정 (dev)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_dev;

-- 앱 계정 (prod)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_prod;

-- 읽기 전용 계정
GRANT SELECT ON ALL TABLES IN SCHEMA public TO readonly;

-- 신규 테이블 자동 권한 부여
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_prod;
```
