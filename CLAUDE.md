# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

분산 락(Distributed Lock) 구현 패턴을 모아둔 치트시트 프로젝트입니다. 다양한 데이터 저장소를 활용한 분산 락 샘플 코드를 제공합니다.

### 지원하는 분산 락 방식
- **MySQL**: `GET_LOCK()` / `RELEASE_LOCK()` 세션 락
- **PostgreSQL**: Advisory Lock (`pg_advisory_lock`, `pg_try_advisory_lock`)
- **Redis**: `LuaScript` 기반 락, `SETNX` 기반 락, Redisson 등

## 빌드 및 테스트 명령어

```bash
# 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.cheetsheet.distributedlock.SomeTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.cheetsheet.distributedlock.SomeTest.testMethod"

# Testcontainers 기반 개발 서버 실행
./gradlew bootTestRun
```

## 아키텍처

### 멀티 DataSource 설정

이 프로젝트는 MySQL, PostgreSQL, Redis를 동시에 사용합니다. 각 데이터 저장소별로 별도의 DataSource를 구성해야 합니다.

**권장 패키지 구조:**
```
com.cheetsheet.distributedlock
├── config/
│   ├── MysqlDataSourceConfig.java
│   ├── PostgresDataSourceConfig.java
│   └── RedisConfig.java
├── mysql/
│   └── MysqlSessionLockService.java
├── postgres/
│   └── PostgresAdvisoryLockService.java
└── redis/
    └── RedisLockService.java
```

**DataSource 설정 예시 (application.yml):**
```yaml
spring:
  datasource:
    mysql:
      url: jdbc:mysql://localhost:3306/lockdb
      username: root
      password: password
      driver-class-name: com.mysql.cj.jdbc.Driver
    postgres:
      url: jdbc:postgresql://localhost:5432/lockdb
      username: postgres
      password: password
      driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: localhost
      port: 6379
```

**@ConfigurationProperties 활용:**
각 DataSource는 `@ConfigurationProperties`와 `@Qualifier`를 사용하여 분리합니다. JPA를 사용하는 경우 `@EnableJpaRepositories`의 `entityManagerFactoryRef`와 `transactionManagerRef`를 명시적으로 지정해야 합니다.

### 테스트 환경

Testcontainers를 사용하여 MySQL, PostgreSQL, Redis 컨테이너를 자동으로 시작합니다. `@ServiceConnection`을 통해 Spring Boot가 컨테이너 연결 정보를 자동으로 구성합니다.

- 개발 시 `TestDistributedLockApplication.main()`을 실행하면 Testcontainers가 자동으로 시작됩니다.
- Docker가 실행 중이어야 합니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA, Spring Data Redis
- Testcontainers (MySQL, PostgreSQL, Redis)
- Gradle
