# Design Document

## Overview

이 시스템은 분산 환경에서 동시성 제어를 위한 다양한 락 구현 패턴을 제공하는 학습용 샘플 코드 모음입니다. MySQL 세션 락, PostgreSQL Advisory Lock, Redis 기반 락(Lua Script, SETNX) 등 4가지 주요 구현 방식을 제공하며, **Spring AOP와 커스텀 애너테이션을 활용하여 선언적으로 분산 락을 적용**할 수 있도록 설계되었습니다.

개발자는 `@DistributedLock` 애너테이션을 메서드에 선언하는 것만으로 분산 락을 적용할 수 있으며, 락 타입(MySQL, PostgreSQL, Redis)을 선택할 수 있습니다. 시스템은 Spring Boot 3.5.x 기반으로 구축되며, 멀티 DataSource 환경에서 각 데이터 저장소를 독립적으로 관리합니다. Testcontainers를 활용하여 실제 데이터베이스와 캐시 서버를 사용한 통합 테스트를 제공하며, 동시성 시나리오에서의 락 동작을 검증할 수 있습니다.

**핵심 사용 예시:**
```java
@Service
public class InventoryService {
    
    @DistributedLock(key = "#productId", type = LockType.REDIS_LUA, timeout = 10)
    public void decreaseStock(String productId, int quantity) {
        // 재고 감소 로직 - 자동으로 분산 락으로 보호됨
    }
}
```

## Architecture

시스템은 **AOP 기반 선언적 락 관리 아키텍처**를 따르며, 각 락 구현은 독립적인 모듈로 분리됩니다.

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│         (Business Services with @DistributedLock)        │
│              @DistributedLock(type=REDIS_LUA)            │
└─────────────────────────────────────────────────────────┘
                            │
                            ↓ (AOP Interception)
┌─────────────────────────────────────────────────────────┐
│                       AOP Layer                          │
│              DistributedLockAspect                       │
│         (Intercepts @DistributedLock methods)            │
└─────────────────────────────────────────────────────────┘
                            │
                            ↓ (Delegates to appropriate service)
┌─────────────────────────────────────────────────────────┐
│                     Service Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   MySQL      │  │  PostgreSQL  │  │    Redis     │  │
│  │ Lock Service │  │ Lock Service │  │ Lock Service │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                 Configuration Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   MySQL      │  │  PostgreSQL  │  │    Redis     │  │
│  │ DataSource   │  │ DataSource   │  │   Config     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                  Infrastructure Layer                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │    MySQL     │  │  PostgreSQL  │  │    Redis     │  │
│  │   Database   │  │   Database   │  │    Cache     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### AOP 실행 흐름

1. 비즈니스 메서드 호출 (`@DistributedLock` 애너테이션 포함)
2. `DistributedLockAspect`가 메서드 호출을 가로챔
3. 애너테이션에서 락 타입, 키, 타임아웃 정보 추출
4. SpEL(Spring Expression Language)을 사용하여 동적 락 키 생성
5. 적절한 Lock Service를 선택하여 락 획득 시도
6. 락 획득 성공 시 원본 메서드 실행
7. 메서드 실행 완료 후 finally 블록에서 락 해제
8. 예외 발생 시에도 락이 반드시 해제되도록 보장

### 주요 설계 원칙

1. **선언적 프로그래밍**: 애너테이션을 통한 선언적 락 관리로 비즈니스 로직과 락 로직 분리
2. **독립성**: 각 락 구현은 독립적인 서비스로 분리되어 서로 영향을 주지 않습니다.
3. **일관성**: 모든 락 서비스는 공통 인터페이스를 따라 일관된 API를 제공합니다.
4. **격리성**: 각 데이터 저장소는 별도의 DataSource/Connection을 사용하여 격리됩니다.
5. **안전성**: AOP의 Around Advice와 try-finally를 통해 락이 항상 해제됨을 보장합니다.
6. **유연성**: SpEL을 통해 메서드 파라미터 기반의 동적 락 키 생성을 지원합니다.
7. **테스트 가능성**: Testcontainers를 통해 실제 환경과 동일한 테스트 환경을 제공합니다.

## Components and Interfaces

### 1. @DistributedLock Annotation

메서드에 선언하여 분산 락을 적용하는 커스텀 애너테이션입니다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * 락 키 (SpEL 표현식 지원)
     * 예: "product:#productId", "'order:' + #orderId"
     */
    String key();
    
    /**
     * 락 타입 (MySQL, PostgreSQL, Redis 등)
     */
    LockType type() default LockType.REDIS_LUA;
    
    /**
     * 타임아웃 (초)
     */
    int timeout() default 10;
    
    /**
     * 락 획득 실패 시 재시도 횟수
     */
    int retryCount() default 0;
    
    /**
     * 재시도 간격 (밀리초)
     */
    long retryInterval() default 100;
}
```

### 2. LockType Enum

지원하는 락 타입을 정의하는 열거형입니다.

```java
public enum LockType {
    MYSQL_SESSION,      // MySQL GET_LOCK/RELEASE_LOCK
    POSTGRES_ADVISORY,  // PostgreSQL Advisory Lock
    REDIS_LUA,          // Redis Lua Script Lock
    REDIS_SETNX         // Redis SETNX Lock
}
```

### 3. DistributedLockAspect

`@DistributedLock` 애너테이션이 붙은 메서드를 가로채는 AOP Aspect입니다.

```java
@Aspect
@Component
@Slf4j
public class DistributedLockAspect {
    
    private final Map<LockType, DistributedLockService> lockServices;
    private final ExpressionParser parser = new SpelExpressionParser();
    
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 1. SpEL을 사용하여 동적 락 키 생성
        String lockKey = resolveLockKey(distributedLock.key(), joinPoint);
        
        // 2. 락 타입에 따라 적절한 서비스 선택
        DistributedLockService lockService = lockServices.get(distributedLock.type());
        
        // 3. 락 획득 시도 (재시도 로직 포함)
        boolean acquired = acquireLockWithRetry(lockService, lockKey, distributedLock);
        
        if (!acquired) {
            throw new LockAcquisitionException("Failed to acquire lock: " + lockKey);
        }
        
        try {
            // 4. 원본 메서드 실행
            return joinPoint.proceed();
        } finally {
            // 5. 락 해제 (반드시 실행)
            lockService.releaseLock(lockKey);
        }
    }
    
    private String resolveLockKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        // SpEL 표현식 파싱 및 평가
    }
    
    private boolean acquireLockWithRetry(DistributedLockService lockService, 
                                         String lockKey, 
                                         DistributedLock distributedLock) {
        // 재시도 로직 구현
    }
}
```

### 4. Lock Service Interface

모든 락 구현이 따르는 공통 인터페이스입니다.

```java
public interface DistributedLockService {
    /**
     * 락을 획득합니다.
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초)
     * @return 락 획득 성공 여부
     */
    boolean acquireLock(String lockKey, int timeoutSeconds);
    
    /**
     * 락을 해제합니다.
     * @param lockKey 락 식별자
     * @return 락 해제 성공 여부
     */
    boolean releaseLock(String lockKey);
    
    /**
     * 이 서비스가 지원하는 락 타입을 반환합니다.
     */
    LockType getSupportedType();
}
```

### 5. MySQL Session Lock Service

MySQL의 `GET_LOCK()` 및 `RELEASE_LOCK()` 함수를 사용한 세션 락 구현입니다.

**주요 특징:**
- 세션 수준에서 관리되는 락
- 세션이 종료되면 자동으로 락 해제
- 타임아웃 지원
- 동일 세션에서 중첩 락 획득 가능

**구현 방식:**
```sql
-- 락 획득
SELECT GET_LOCK('lockKey', timeoutSeconds)

-- 락 해제
SELECT RELEASE_LOCK('lockKey')
```

**서비스 구현:**
```java
@Service
public class MysqlSessionLockService implements DistributedLockService {
    
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public LockType getSupportedType() {
        return LockType.MYSQL_SESSION;
    }
    
    // acquireLock, releaseLock 구현
}
```

### 6. PostgreSQL Advisory Lock Service

PostgreSQL의 Advisory Lock 함수를 사용한 락 구현입니다.

**주요 특징:**
- 애플리케이션 레벨의 락
- 블로킹(`pg_advisory_lock`) 및 논블로킹(`pg_try_advisory_lock`) 방식 지원
- 문자열 키를 정수 해시로 변환하여 사용
- 트랜잭션 레벨 또는 세션 레벨 락 선택 가능

**구현 방식:**
```sql
-- 블로킹 방식 락 획득
SELECT pg_advisory_lock(hashcode)

-- 논블로킹 방식 락 획득
SELECT pg_try_advisory_lock(hashcode)

-- 락 해제
SELECT pg_advisory_unlock(hashcode)
```

**해시 함수:**
문자열 Lock Key를 정수로 변환하기 위해 Java의 `String.hashCode()`를 사용합니다.

**서비스 구현:**
```java
@Service
public class PostgresAdvisoryLockService implements DistributedLockService {
    
    @Qualifier("postgresJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public LockType getSupportedType() {
        return LockType.POSTGRES_ADVISORY;
    }
    
    private long hashLockKey(String lockKey) {
        return (long) lockKey.hashCode();
    }
    
    // acquireLock, releaseLock 구현
}
```

### 7. Redis Lua Script Lock Service

Redis의 Lua 스크립트를 사용하여 원자적 연산을 보장하는 락 구현입니다.

**주요 특징:**
- Lua 스크립트를 통한 원자적 연산
- 락 소유자 식별을 위한 고유 값 저장
- 자동 만료 시간 설정
- 락 해제 시 소유자 검증

**Lua 스크립트 - 락 획득:**
```lua
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
    return 1
else
    return 0
end
```

**Lua 스크립트 - 락 해제:**
```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

**서비스 구현:**
```java
@Service
public class RedisLuaLockService implements DistributedLockService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final String ownerId = UUID.randomUUID().toString();
    
    @Override
    public LockType getSupportedType() {
        return LockType.REDIS_LUA;
    }
    
    // Lua 스크립트를 사용한 acquireLock, releaseLock 구현
}
```

### 8. Redis SETNX Lock Service

Redis의 `SETNX` 명령을 사용한 간단한 락 구현입니다.

**주요 특징:**
- SET if Not eXists 명령 사용
- 간단한 구현
- 만료 시간 설정으로 데드락 방지
- 락 소유자 식별 지원

**구현 방식:**
```java
// 락 획득 (SET NX EX를 한 번에)
SET lockKey uniqueValue NX EX timeoutSeconds

// 락 해제
DEL lockKey
```

**서비스 구현:**
```java
@Service
public class RedisSetnxLockService implements DistributedLockService {
    
    private final StringRedisTemplate redisTemplate;
    private final String ownerId = UUID.randomUUID().toString();
    
    @Override
    public LockType getSupportedType() {
        return LockType.REDIS_SETNX;
    }
    
    // SETNX를 사용한 acquireLock, releaseLock 구현
}
```

### 9. Configuration Components

#### AOP Configuration
```java
@Configuration
@EnableAspectJAutoProxy
public class DistributedLockConfig {
    
    @Bean
    public DistributedLockAspect distributedLockAspect(List<DistributedLockService> lockServices) {
        return new DistributedLockAspect(lockServices);
    }
}
```

#### MySQL DataSource Configuration
```java
@Configuration
public class MysqlDataSourceConfig {
    @Primary
    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Primary
    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate() {
        return new JdbcTemplate(mysqlDataSource());
    }
}
```

#### PostgreSQL DataSource Configuration
```java
@Configuration
public class PostgresDataSourceConfig {
    @Bean(name = "postgresDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.postgresql")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate() {
        return new JdbcTemplate(postgresDataSource());
    }
}
```

#### Redis Configuration
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
    
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

## Data Models

### LockResult

락 작업의 결과를 나타내는 값 객체입니다.

```java
public class LockResult {
    private final boolean success;
    private final String lockKey;
    private final String message;
    private final Instant timestamp;
    
    // Constructor, getters, toString
}
```

### LockMetadata

락의 메타데이터를 저장하는 객체입니다 (Redis 구현에서 사용).

```java
public class LockMetadata {
    private final String lockKey;
    private final String ownerId;
    private final Instant acquiredAt;
    private final int timeoutSeconds;
    
    // Constructor, getters, toString
}
```

### LockException

락 작업 중 발생하는 예외를 나타내는 커스텀 예외입니다.

```java
public class LockException extends RuntimeException {
    private final String lockKey;
    private final LockOperation operation;
    
    public enum LockOperation {
        ACQUIRE, RELEASE, TIMEOUT
    }
    
    // Constructors
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### MySQL Session Lock Properties

**Property 1: MySQL 락 획득 성공 반환**
*For any* valid Lock Key and positive Timeout value, when acquiring a MySQL session lock, the system should invoke GET_LOCK and return a boolean result indicating success or failure.
**Validates: Requirements 1.1**

**Property 2: MySQL 락 해제 성공 반환**
*For any* acquired MySQL session lock, when releasing the lock, the system should invoke RELEASE_LOCK and return a boolean result indicating success or failure.
**Validates: Requirements 1.2**

**Property 3: MySQL 락 타임아웃 실패**
*For any* Lock Key that is already held, when attempting to acquire the same lock with a timeout value, if the timeout expires before the lock becomes available, the system should return failure.
**Validates: Requirements 1.3**

**Property 4: MySQL 상호 배제**
*For any* Lock Key, when multiple threads concurrently attempt to acquire the same lock, at most one thread should successfully acquire the lock at any given time.
**Validates: Requirements 1.4**

### PostgreSQL Advisory Lock Properties

**Property 5: PostgreSQL 블로킹 락 획득**
*For any* valid Lock Key, when requesting a blocking advisory lock acquisition, the system should invoke pg_advisory_lock and wait until the lock is acquired.
**Validates: Requirements 2.1**

**Property 6: PostgreSQL 논블로킹 락 즉시 반환**
*For any* valid Lock Key, when requesting a non-blocking advisory lock acquisition, the system should invoke pg_try_advisory_lock and immediately return success or failure without waiting.
**Validates: Requirements 2.2**

**Property 7: PostgreSQL 락 해제 성공 반환**
*For any* acquired PostgreSQL advisory lock, when releasing the lock, the system should invoke pg_advisory_unlock and return a boolean result indicating success or failure.
**Validates: Requirements 2.3**

**Property 8: 문자열 키 해시 변환 결정성**
*For any* string Lock Key, when converting to an integer hash for advisory lock functions, the same string should always produce the same hash value (deterministic conversion).
**Validates: Requirements 2.4**

### Redis Lua Script Lock Properties

**Property 9: Lua 스크립트 원자적 락 획득**
*For any* valid Lock Key and Timeout value, when acquiring a lock using Lua script, the system should atomically set the lock with the specified expiration time.
**Validates: Requirements 3.1**

**Property 10: Lua 스크립트 소유자 검증 해제**
*For any* acquired lock with an owner identifier, when releasing the lock, only the correct owner should be able to successfully release it; attempts by other owners should fail.
**Validates: Requirements 3.2**

**Property 11: Redis 상호 배제**
*For any* Lock Key, when a lock already exists and another client attempts to acquire the same lock, the acquisition should fail.
**Validates: Requirements 3.3**

**Property 12: Redis 자동 만료**
*For any* lock with a specified expiration time, after the expiration time elapses, the lock should be automatically released and become available for other clients to acquire.
**Validates: Requirements 3.4**

### Redis SETNX Lock Properties

**Property 13: SETNX 락 획득 및 만료 설정**
*For any* valid Lock Key and Timeout value, when acquiring a lock using SETNX, the system should use SET NX EX command to atomically set the lock with expiration.
**Validates: Requirements 4.1**

**Property 14: SETNX 락 해제**
*For any* acquired SETNX lock, when releasing the lock, the system should use DEL command to remove the lock key.
**Validates: Requirements 4.2**

**Property 15: SETNX 기존 키 실패**
*For any* Lock Key that already exists, when executing SETNX command, the acquisition should fail and return false.
**Validates: Requirements 4.3**

**Property 16: SETNX 기본 만료 시간 적용**
*For any* lock acquisition request without a specified timeout or with a timeout of zero, the system should apply a default expiration time to prevent deadlocks.
**Validates: Requirements 4.4**

### Cross-Implementation Properties

**Property 17: 락 획득-해제 사이클**
*For any* Lock Key and any lock implementation, after successfully acquiring and then releasing a lock, another client should be able to successfully acquire the same lock.
**Validates: Requirements 5.3**

**Property 18: 존재하지 않는 락 해제 실패**
*For any* Lock Key that does not correspond to an existing lock, when attempting to release the lock, the system should return failure.
**Validates: Requirements 7.3**

### AOP-Based Annotation Properties

**Property 19: AOP 메서드 인터셉션**
*For any* method annotated with @DistributedLock, when the method is invoked, the AOP aspect should intercept the call and acquire the lock before executing the method body.
**Validates: Requirements 9.1**

**Property 20: SpEL 동적 락 키 생성**
*For any* SpEL expression in the @DistributedLock annotation and any method parameters, the system should correctly evaluate the expression and generate the appropriate lock key based on the parameter values.
**Validates: Requirements 9.2**

**Property 21: 락 타입 기반 서비스 선택**
*For any* LockType specified in the @DistributedLock annotation, the system should select and use the corresponding DistributedLockService implementation.
**Validates: Requirements 9.3**

**Property 22: 락 해제 보장**
*For any* method annotated with @DistributedLock, regardless of whether the method completes successfully or throws an exception, the acquired lock should always be released in the finally block.
**Validates: Requirements 9.4**

**Property 23: 재시도 로직**
*For any* @DistributedLock annotation with retry configuration (retryCount > 0), when lock acquisition fails, the system should retry the specified number of times with the specified interval before throwing an exception.
**Validates: Requirements 9.5**

## Error Handling

### Exception Hierarchy

```java
LockException (RuntimeException)
├── LockAcquisitionException
│   ├── LockTimeoutException
│   └── LockAlreadyHeldException
├── LockReleaseException
│   └── LockNotHeldException
└── LockConnectionException
```

### Error Scenarios

1. **데이터베이스 연결 실패**
   - Exception: `LockConnectionException`
   - Message: "Failed to connect to [MySQL/PostgreSQL/Redis]: {error details}"
   - Logging: ERROR level with stack trace

2. **락 획득 타임아웃**
   - Exception: `LockTimeoutException`
   - Message: "Lock acquisition timed out for key '{lockKey}' after {timeout} seconds"
   - Logging: WARN level

3. **락 이미 보유 중**
   - Exception: `LockAlreadyHeldException`
   - Message: "Lock '{lockKey}' is already held by another client"
   - Logging: DEBUG level

4. **존재하지 않는 락 해제**
   - Return: `false`
   - Logging: WARN level with message "Attempted to release non-existent lock '{lockKey}'"

5. **잘못된 락 소유자**
   - Exception: `LockReleaseException`
   - Message: "Lock '{lockKey}' cannot be released by current owner"
   - Logging: WARN level

### Logging Strategy

- **DEBUG**: 정상적인 락 획득/해제 작업
- **INFO**: 락 서비스 초기화, 설정 로딩
- **WARN**: 타임아웃, 존재하지 않는 락 해제 시도, 재시도 로직
- **ERROR**: 연결 실패, 예상치 못한 예외, 시스템 오류

모든 로그는 다음 정보를 포함합니다:
- Lock Key
- Operation (ACQUIRE/RELEASE)
- Timestamp
- Thread ID (동시성 디버깅용)
- Error details (예외 발생 시)

## Testing Strategy

### Unit Testing

각 Lock Service 구현에 대해 다음 시나리오를 테스트합니다:

1. **기본 동작 테스트**
   - 락 획득 성공
   - 락 해제 성공
   - 락 획득 실패 (이미 보유 중)

2. **에지 케이스 테스트**
   - 빈 문자열 Lock Key
   - 매우 긴 Lock Key
   - 특수 문자가 포함된 Lock Key
   - 0 또는 음수 Timeout
   - 매우 큰 Timeout 값

3. **에러 처리 테스트**
   - 연결 실패 시나리오
   - 존재하지 않는 락 해제
   - 잘못된 소유자의 락 해제 시도

### Property-Based Testing

이 프로젝트는 **JUnit 5**의 `@RepeatedTest`와 `@ParameterizedTest`, 그리고 **AssertJ**를 사용하여 property-based testing을 구현합니다.

**설정:**
```gradle
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
testImplementation 'org.assertj:assertj-core:3.24.2'
```

**각 property-based test는 최소 100회 반복 실행되도록 설정합니다:**
```java
@RepeatedTest(100)
```

**Property-based test 작성 규칙:**
1. 각 테스트는 설계 문서의 correctness property를 명시적으로 참조하는 주석을 포함해야 합니다.
2. 주석 형식: `// Feature: distributed-lock-samples, Property {number}: {property_text}`
3. 각 correctness property는 하나의 property-based test로 구현됩니다.
4. 테스트 메서드 내에서 랜덤 데이터를 생성하여 다양한 입력값을 테스트합니다.
5. **AssertJ의 유창한(fluent) API를 사용하여 가독성 높은 검증을 작성합니다.**

**Generator 전략:**
- Lock Key: 임의의 영숫자 문자열 (1-100자) - `RandomStringUtils` 또는 `UUID` 사용
- Timeout: 1-60초 범위의 임의의 정수 - `ThreadLocalRandom` 사용
- Thread Count: 2-10개 범위의 임의의 정수 (동시성 테스트용)
- Owner ID: UUID 기반 고유 식별자

**Property Test 예시 (AssertJ 사용):**
```java
import static org.assertj.core.api.Assertions.*;

@RepeatedTest(100)
// Feature: distributed-lock-samples, Property 4: MySQL 상호 배제
void mysqlMutualExclusion() {
    // 랜덤 Lock Key 생성
    String lockKey = UUID.randomUUID().toString().substring(0, 20);
    int timeout = ThreadLocalRandom.current().nextInt(1, 61);
    
    // 첫 번째 락 획득
    boolean firstAcquired = lockService.acquireLock(lockKey, timeout);
    assertThat(firstAcquired).isTrue();
    
    // 동일한 키로 두 번째 락 획득 시도 (실패해야 함)
    boolean secondAcquired = lockService.acquireLock(lockKey, 1);
    assertThat(secondAcquired).isFalse();
    
    // 락 해제
    boolean released = lockService.releaseLock(lockKey);
    assertThat(released).isTrue();
    
    // 해제 후 다시 획득 가능해야 함
    boolean reacquired = lockService.acquireLock(lockKey, timeout);
    assertThat(reacquired).isTrue();
}
```

**AssertJ 주요 활용 패턴:**
- `assertThat(value).isTrue()` / `isFalse()`: boolean 검증
- `assertThat(value).isEqualTo(expected)`: 값 동등성 검증
- `assertThat(value).isNotNull()`: null 검증
- `assertThat(collection).hasSize(n)`: 컬렉션 크기 검증
- `assertThat(collection).containsExactly(...)`: 컬렉션 내용 검증
- `assertThatThrownBy(() -> ...).isInstanceOf(Exception.class)`: 예외 검증
- `assertThat(value).satisfies(condition)`: 커스텀 조건 검증

### Integration Testing

Testcontainers를 사용한 통합 테스트:

```java
@Testcontainers
@SpringBootTest
class DistributedLockIntegrationTest {
    
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    // Test methods
}
```

**통합 테스트 시나리오:**
1. 멀티 스레드 동시성 테스트
2. 락 타임아웃 동작 검증
3. 자동 만료 동작 검증
4. 실제 비즈니스 시나리오 시뮬레이션 (재고 감소 등)

### Performance Testing

각 락 구현의 성능 특성을 측정합니다:

1. **처리량 (Throughput)**
   - 초당 락 획득/해제 횟수
   
2. **지연 시간 (Latency)**
   - 락 획득 평균/최대 시간
   - 락 해제 평균/최대 시간

3. **동시성 성능**
   - 다양한 스레드 수에서의 성능 측정
   - 경합(contention) 상황에서의 동작

**성능 테스트는 선택적이며, 학습 목적의 샘플 코드에서는 필수가 아닙니다.**
