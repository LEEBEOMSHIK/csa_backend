-- V6: add role/status to users for the upcoming admin site (no role/permission concept existed before).
-- Identifiers are unquoted lowercase snake_case to match Hibernate's runtime
-- PhysicalNamingStrategySnakeCaseImpl (Spring Boot 4 default), so ddl-auto: validate
-- passes against this schema. Applied in the schema configured by
-- spring.flyway.default-schema (front).

alter table users add column role varchar(20) not null default 'USER';
alter table users add column status varchar(20) not null default 'ACTIVE';
