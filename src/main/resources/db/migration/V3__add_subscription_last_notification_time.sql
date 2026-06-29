-- V3: Out-of-order guard precision (W1). Adds the event-time of the last applied
-- store lifecycle notification so the order guard compares notification event-time
-- vs notification event-time, instead of event-time vs the processing wall-clock
-- (updated_at). Nullable: a subscription that has never had a notification applied
-- has no prior event-time. Identifier is unquoted lowercase snake_case to match
-- Hibernate's PhysicalNamingStrategySnakeCaseImpl (Spring Boot 4 default), so
-- ddl-auto: validate passes. Type mirrors the Subscription JPA entity exactly.

alter table subscription
    add column last_notification_time timestamp(6);
