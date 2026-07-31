# migrations.md — DB 마이그레이션 정리

`csa_backend`는 Flyway로 스키마를 관리한다. 이 문서는 다른 로컬 환경에서 저장소를 클론했을 때 동일한 DB 스키마 상태를 재현·확인할 수 있도록 마이그레이션 이력과 절차를 정리한다.

## 1. Flyway 동작 방식

- **Flyway가 스키마의 단일 소스(single source of truth)다.** `src/main/resources/db/migration/`의 `V{n}__설명.sql` 파일이 곧 스키마 정의이며, JPA Entity는 이 스키마를 따라간다.
- `spring.jpa.hibernate.ddl-auto: validate` (`application-local.yaml`) — Hibernate는 스키마를 직접 생성/변경하지 않고, 기동 시 Entity 매핑이 Flyway가 만든 실제 테이블과 일치하는지 검증만 한다. 불일치 시 기동이 실패한다.
- 마이그레이션은 `spring.flyway.default-schema` / `spring.flyway.schemas` 설정에 따라 **`front` 스키마**(`public`이 아님)에 적용된다. `spring.flyway.create-schemas: true`로 스키마 자체도 Flyway가 생성한다.
- 적용 이력은 `front.flyway_schema_history` 테이블에 버전(`version`), 파일명(`script`), 체크섬(`checksum`), 적용 시각 등으로 기록된다. 로컬에서 실제 적용 상태를 확인하려면 이 테이블을 조회하면 된다:
  ```sql
  select version, description, script, installed_on, success
  from front.flyway_schema_history
  order by installed_rank;
  ```
- 애플리케이션 기동(`./gradlew bootRun` 등) 시 Flyway가 `flyway_schema_history`에 없는 버전을 오름차순으로 자동 적용한다. 별도의 수동 마이그레이션 명령이 필요 없다.

> 이 문서 작성 시점에는 로컬 Postgres 컨테이너(`csa_backend-postgres-1`)가 기동되어 있지 않아 `flyway_schema_history`를 직접 조회하지 못했다. 아래 V1~V10 요약은 마이그레이션 SQL 파일을 직접 읽고 정리한 내용이며, 실제 로컬 DB의 적용 여부·순서는 위 쿼리로 별도 확인해야 한다.

## 2. 로컬에서 동일한 스키마 재현하는 절차

1. 저장소 루트(`csa_backend/`)에서 Postgres 컨테이너를 기동한다.
   ```bash
   docker compose up -d
   ```
   `compose.yaml` 기준으로 `postgres:latest` 이미지가 `localhost:15432`에 `csa` DB(`myuser`/`secret`)로 뜬다. 데이터는 `postgres_data` 볼륨에 영속화된다.
2. 백엔드를 기동한다.
   ```bash
   ./gradlew bootRun
   # Windows
   .\gradlew.bat bootRun
   ```
   기동 시 `spring-boot-docker-compose` 연동으로 Postgres가 없으면 자동으로 같이 뜨고(이미 떠 있으면 그대로 사용), Flyway가 `front` 스키마에 V1부터 현재 최신 버전(V11)까지 순서대로 적용한다.
3. 기동이 끝나면 `front.flyway_schema_history`에 V1~V11 전부 `success = true`로 기록되어 있어야 정상이다. 클론한 두 로컬 환경이 같은 마이그레이션 파일 세트를 가지고 있다면 이 이력도 동일하게 재현된다.

## 3. 트러블슈팅 (요약)

아래 두 문제는 루트 `CLAUDE.md`에 상세 원인·해결 절차가 있다. 여기서는 핵심만 옮긴다. **자세한 내용은 루트 `CLAUDE.md`의 "트러블슈팅" 섹션 참고.**

- **`database "xxx" does not exist`**: DB명 변경 후에도 기존 `postgres_data` 볼륨이 예전 DB명으로 이미 초기화되어 있으면 `POSTGRES_DB`가 재적용되지 않아 발생한다. `docker compose down -v` (볼륨 삭제, 데이터 손실 주의) 후 `docker compose up -d --build`로 해결한다.
- **`down -v` 후 Entity 컬럼 누락**: `docker compose down -v` 이후 `--build` 없이 `up -d`만 하면 캐시된 구 이미지 기준으로 뜨기 때문에, 새로 추가한 마이그레이션/컬럼이 반영되지 않는다. `down -v` 이후 재기동은 **반드시 `--build`를 함께 사용**한다.

## 4. 마이그레이션 이력 (V1~V11)

| 버전 | 파일명 | 요약 |
|---|---|---|
| V1 | `V1__init.sql` | 베이스라인 스키마. `users`, `refresh_tokens`, `user_settings`, `term_agreements`, `favorites`, `fairytales`, `fairytale_details`, `categories`, `fairytale_categories`(조인 테이블), `characters`, `ai_fairytales`, `ai_fairytale_pages` 총 12개 테이블과 각 FK 제약을 생성한다. |
| V2 | `V2__add_subscription.sql` | IAP 단일 결제 구독 도메인 테이블 `subscription`을 추가한다(`platform` APPLE/GOOGLE, `status` ACTIVE/EXPIRED/CANCELED/GRACE, `environment` SANDBOX/PRODUCTION 등 CHECK 제약 포함). `user_id` 인덱스, `original_transaction_id` 유니크 인덱스, `users` FK를 함께 생성한다. |
| V3 | `V3__add_subscription_last_notification_time.sql` | `subscription`에 `last_notification_time` (nullable timestamp) 컬럼을 추가한다. 스토어 알림의 순서 검증을 "처리 시각(`updated_at`)" 대신 "알림 이벤트 시각끼리" 비교하도록 정밀화하기 위함이다. |
| V4 | `V4__add_curated_fairytale_slide_manifest.sql` | `fairytales`에 `character_supported`(boolean, not null, default false), `fairytale_details`에 `content_version`(varchar(50), nullable) 컬럼을 추가한다. 신규 테이블 `curated_fairytale_pages`(페이지별 이미지·ko/ja 텍스트·캐릭터 배치 정보, `(fairytale_id, page_index)` 유니크, 배치 좌표 CHECK 제약 포함)와 `curated_fairytale_audios`(페이지별 음성 타입·locale·오디오 URL, `(page_id, voice_type, locale)` 유니크)를 생성하고 FK를 건다. |
| V5 | `V5__version_curated_fairytale_page_unique_key.sql` | `curated_fairytale_pages`의 유니크 제약을 `(fairytale_id, page_index)`에서 `(fairytale_id, content_version, page_index)`로 교체한다. 콘텐츠 버전별로 페이지를 동시에 staging할 수 있도록 하기 위함이다. |
| V6 | `V6__add_user_role_and_status.sql` | `users`에 `role`(varchar(20), not null, default `'USER'`), `status`(varchar(20), not null, default `'ACTIVE'`) 컬럼을 추가한다. 관리자 사이트(`csa_adm_frontend`)를 위한 최초의 권한/상태 개념 도입이다. |
| V7 | `V7__add_reports.sql` | 신고 도메인 테이블 `reports`를 추가한다. `reporter_id`(FK), `target_type`/`target_id`(다형 참조), `reason`, `detail`, `status`(default `'PENDING'`), 관리자 처리용 `admin_note`/`resolved_by`(FK)/`resolved_at`, BaseEntity 감사 컬럼(`cre_dt`~`del_yn`)을 갖는다. `status`, `(target_type, target_id)` 인덱스를 생성한다. |
| V8 | `V8__add_admin_audit_log.sql` | 관리자 감사 로그 테이블 `admin_audit_log`를 추가한다(`admin_user_id` FK, `admin_email`, `action`, `target_type`/`target_id`, `detail`, `created_at`). `csa_adm_backend`가 전용으로 읽고 쓰며, `csa_backend`는 마이그레이션(스키마 소유권)만 갖고 이 쪽에는 대응 Entity를 추가하지 않는다. `target_type/target_id`, `admin_user_id`, `created_at` 인덱스를 생성한다. |
| V9 | `V9__add_fairytale_download_log.sql` | 동화 다운로드 이벤트 로그 테이블 `fairytale_download_log`를 추가한다. `target_type`(`FAIRYTALE`/`AI_FAIRYTALE`) 다형 참조에 `fairytale_id`/`ai_fairytale_id` 두 nullable FK를 두고, 타입별로 정확히 하나만 채워지도록 CHECK 제약을 건다. `format`(`slide`/`video`) CHECK 제약도 포함하며, append-only 로그라 BaseEntity 감사 컬럼 대신 단순 `created_at`만 쓴다(V8과 동일 패턴). 관련 인덱스 4개를 생성한다. |
| V10 | `V10__add_ai_fairytale_video_url.sql` | `ai_fairytales`에 `video_url`(varchar(1000), nullable) 컬럼을 추가한다. 서버 측 ffmpeg 슬라이드+TTS 합성 결과(mp4, `format = 'video'`)의 URL을 저장하기 위함이며, 슬라이드 포맷 행이나 합성 실패 전까지의 비디오 포맷 행에서는 null이다. |
| V11 | `V11__add_subscription_history.sql` | 구독 상태 전이 이력 테이블 `subscription_history`를 추가한다. `subscription`은 구독 1건당 1행이라 갱신 시 과거 상태가 사라지므로, 관리자 화면의 "구독 상태 변경 이력"을 위해 전이를 append-only로 남긴다. `subscription_id` FK, `previous_status`/`new_status`(`subscription.status`와 동일한 CHECK), `previous_period_end`/`new_period_end`, `previous_auto_renew`/`new_auto_renew`, `source`(`CREATED`/`VERIFICATION`/`STORE_NOTIFICATION`/`SUPERSEDED`/`EXPIRY_SWEEP` CHECK), `created_at`을 갖는다. `previous_*`는 최초 생성 행에서 null이다. append-only 로그라 BaseEntity 감사 컬럼 대신 단순 `created_at`만 쓴다(V8/V9와 동일 패턴). `(subscription_id, created_at)` 복합 인덱스와 `created_at` 인덱스를 생성한다. 쓰기는 `csa_backend`의 `SubscriptionService`가 담당하고, 조회는 `csa_adm_backend`가 맡는다. |

## 5. 새 마이그레이션 추가 규칙

- 다음 버전 번호부터 시작한다 — 현재 최신은 V11이므로 다음은 **V12**.
- 파일명 컨벤션: `V{n}__snake_case_설명.sql` (예: `V11__add_something.sql`).
- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다.** Flyway는 각 파일의 체크섬을 `flyway_schema_history`에 기록해두고 기동 시마다 비교하는데, 기존 파일 내용을 바꾸면 체크섬이 달라져 애플리케이션 기동이 실패한다. 스키마를 고치고 싶다면 새 버전 파일로 `ALTER`를 추가한다.
- 컬럼/테이블명은 언더스코어 스네이크케이스 소문자로 작성한다 (`db-guidelines.md` 원칙과 달리 이 저장소의 실제 마이그레이션은 Hibernate의 `PhysicalNamingStrategySnakeCaseImpl` 기본 동작에 맞춰 소문자 unquoted 식별자를 사용한다 — 각 마이그레이션 파일 상단 주석 참고).
- `NOT NULL` 컬럼을 기존 테이블에 추가할 때는 기존 데이터를 위해 `DEFAULT` 값을 지정한다 (V6 참고).
- 마이그레이션 작성 후에는 대응하는 JPA Entity를 함께 갱신해 `ddl-auto: validate`가 통과하는지 확인한다.
