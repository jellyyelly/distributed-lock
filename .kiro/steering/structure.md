---
inclusion: always
---

# Project Structure

## Package Organization

```
com.cheetsheet.distributedlock/
├── annotation/          # Custom annotations (e.g., @DistributedLock)
├── config/             # DataSource and infrastructure configuration
│   ├── MysqlDataSourceConfig.java
│   ├── PostgresDataSourceConfig.java
│   └── RedisConfig.java
├── enums/              # Enumerations (e.g., LockType)
├── exception/          # Custom exceptions for lock operations
│   ├── LockException.java (base)
│   ├── LockAcquisitionException.java
│   ├── LockReleaseException.java
│   ├── LockTimeoutException.java
│   └── ...
├── model/              # Domain models and DTOs
│   ├── LockMetadata.java
│   └── LockResult.java
├── service/            # Business logic and lock implementations
│   └── DistributedLockService.java
└── util/               # Utility classes (e.g., SpelKeyResolver)
```

## Multi-DataSource Architecture

The project uses separate DataSource configurations for each database:

- **MySQL DataSource**: For MySQL session locks
- **PostgreSQL DataSource**: For PostgreSQL advisory locks  
- **Redis**: For Redis-based distributed locks

When using JPA, explicitly specify `entityManagerFactoryRef` and `transactionManagerRef` in `@EnableJpaRepositories` for each DataSource.

## Test Structure

```
src/test/java/com/cheetsheet/distributedlock/
├── TestDistributedLockApplication.java    # Main class for dev with Testcontainers
├── TestcontainersConfiguration.java       # Testcontainers setup with @ServiceConnection
└── util/                                  # Test utilities
```

## Sample Code

The `sample/` directory contains reference implementations:
- `DistributeLock.java`: Annotation interface example
- `LuaScriptLockManager.java`: Redis Lua script lock implementation
- `SimpleDistributedLock.java`: Basic lock wrapper
- `SimpleDistributeLockAspect.java`: AOP-based lock handling

## Configuration Files

- `application.yml`: Multi-datasource configuration with separate sections for mysql, postgresql, and redis
- `build.gradle`: Dependencies and build configuration
- `docker-compose.yml`: Local development database setup
