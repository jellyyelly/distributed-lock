---
inclusion: always
---

# Technology Stack

## Core Technologies

- **Java 21** with toolchain configuration
- **Spring Boot 3.5.x**
- **Gradle** build system
- **Lombok** for boilerplate reduction

## Frameworks & Libraries

- Spring Data JPA
- Spring Data Redis
- MySQL Connector
- PostgreSQL Driver
- H2 Database (for testing)

## Testing

- JUnit 5 (JUnit Platform)
- Testcontainers (MySQL, PostgreSQL, Redis)
- Spring Boot Test

## Common Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.cheetsheet.distributedlock.ClassName"

# Run specific test method
./gradlew test --tests "com.cheetsheet.distributedlock.ClassName.methodName"

# Run with Testcontainers (development mode)
./gradlew bootTestRun
```

## Development Environment

- **Docker** required for Testcontainers
- Use `TestDistributedLockApplication.main()` to start the application with Testcontainers auto-configuration
- Docker Compose available for manual database setup (mysql, postgresql, redis)

## Database Configuration

The project uses multiple DataSources simultaneously:
- MySQL on port 3306
- PostgreSQL on port 5432
- Redis on port 6379

Each DataSource is configured separately using `@ConfigurationProperties` and `@Qualifier` annotations.
