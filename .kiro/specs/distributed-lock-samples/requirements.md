# Requirements Document

## Introduction

이 프로젝트는 분산 시스템에서 동시성 제어를 위한 분산 락(Distributed Lock) 구현 패턴을 학습할 수 있는 샘플 코드 모음입니다. MySQL, PostgreSQL, Redis 등 다양한 데이터 저장소를 활용한 분산 락 구현 방식을 제공하며, 각 방식의 특징과 사용법을 실제 동작하는 코드를 통해 이해할 수 있도록 합니다.

## Glossary

- **Distributed Lock System**: 분산 환경에서 여러 프로세스나 서버가 공유 자원에 대한 접근을 제어하기 위한 락 메커니즘을 제공하는 시스템
- **Lock Service**: 특정 데이터 저장소를 사용하여 락 획득, 해제, 타임아웃 등의 기능을 제공하는 서비스 컴포넌트
- **Lock Key**: 락을 식별하기 위한 고유한 문자열 식별자
- **Lock Timeout**: 락이 자동으로 해제되기까지의 최대 대기 시간 (초 단위)
- **Session Lock**: 데이터베이스 세션 수준에서 관리되는 락으로, 세션이 종료되면 자동으로 해제됨
- **Advisory Lock**: PostgreSQL에서 제공하는 애플리케이션 레벨의 락으로, 개발자가 명시적으로 관리함
- **Lua Script Lock**: Redis에서 Lua 스크립트를 사용하여 원자적으로 락을 획득하고 해제하는 방식
- **SETNX Lock**: Redis의 SET if Not eXists 명령을 사용한 락 구현 방식
- **Test Container**: 테스트 환경에서 실제 데이터베이스나 캐시 서버를 Docker 컨테이너로 자동 실행하는 도구

## Requirements

### Requirement 1

**User Story:** 개발자로서, MySQL 세션 락을 사용한 분산 락 구현을 학습하고 싶습니다. 그래서 GET_LOCK과 RELEASE_LOCK을 활용한 샘플 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 Lock Key와 Timeout을 제공하여 락 획득을 요청하면 THEN THE Distributed Lock System SHALL MySQL의 GET_LOCK 함수를 호출하여 락을 획득하고 성공 여부를 반환한다
2. WHEN 개발자가 획득한 락을 해제 요청하면 THEN THE Distributed Lock System SHALL MySQL의 RELEASE_LOCK 함수를 호출하여 락을 해제하고 성공 여부를 반환한다
3. WHEN 락 획득 시도가 타임아웃 시간을 초과하면 THEN THE Distributed Lock System SHALL 락 획득 실패를 반환한다
4. WHEN 동일한 Lock Key로 여러 스레드가 동시에 락 획득을 시도하면 THEN THE Distributed Lock System SHALL 하나의 스레드만 락을 획득하고 나머지는 대기하거나 실패를 반환한다

### Requirement 2

**User Story:** 개발자로서, PostgreSQL Advisory Lock을 사용한 분산 락 구현을 학습하고 싶습니다. 그래서 pg_advisory_lock과 pg_try_advisory_lock을 활용한 샘플 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 Lock Key를 제공하여 블로킹 방식의 락 획득을 요청하면 THEN THE Distributed Lock System SHALL PostgreSQL의 pg_advisory_lock 함수를 호출하여 락을 획득할 때까지 대기한다
2. WHEN 개발자가 Lock Key를 제공하여 논블로킹 방식의 락 획득을 요청하면 THEN THE Distributed Lock System SHALL PostgreSQL의 pg_try_advisory_lock 함수를 호출하여 즉시 성공 여부를 반환한다
3. WHEN 개발자가 획득한 Advisory Lock을 해제 요청하면 THEN THE Distributed Lock System SHALL pg_advisory_unlock 함수를 호출하여 락을 해제하고 성공 여부를 반환한다
4. WHEN Lock Key가 문자열로 제공되면 THEN THE Distributed Lock System SHALL 문자열을 정수 해시값으로 변환하여 Advisory Lock 함수에 전달한다

### Requirement 3

**User Story:** 개발자로서, Redis Lua Script를 사용한 분산 락 구현을 학습하고 싶습니다. 그래서 원자적 연산을 보장하는 Lua 스크립트 기반 락 샘플 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 Lock Key와 Timeout을 제공하여 락 획득을 요청하면 THEN THE Distributed Lock System SHALL Lua 스크립트를 사용하여 원자적으로 락을 설정하고 만료 시간을 지정한다
2. WHEN 개발자가 획득한 락을 해제 요청하면 THEN THE Distributed Lock System SHALL Lua 스크립트를 사용하여 락 소유자를 확인한 후 원자적으로 락을 삭제한다
3. WHEN 락이 이미 존재하는 상태에서 다른 클라이언트가 락 획득을 시도하면 THEN THE Distributed Lock System SHALL 락 획득 실패를 반환한다
4. WHEN 락의 만료 시간이 경과하면 THEN THE Distributed Lock System SHALL 자동으로 락을 해제하여 다른 클라이언트가 획득할 수 있도록 한다

### Requirement 4

**User Story:** 개발자로서, Redis SETNX를 사용한 분산 락 구현을 학습하고 싶습니다. 그래서 SET if Not eXists 명령을 활용한 간단한 락 샘플 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 Lock Key와 Timeout을 제공하여 락 획득을 요청하면 THEN THE Distributed Lock System SHALL Redis의 SETNX 명령과 EXPIRE 명령을 사용하여 락을 설정한다
2. WHEN 개발자가 획득한 락을 해제 요청하면 THEN THE Distributed Lock System SHALL Redis의 DEL 명령을 사용하여 락 키를 삭제한다
3. WHEN 락 키가 이미 존재하는 상태에서 SETNX 명령을 실행하면 THEN THE Distributed Lock System SHALL 락 획득 실패를 반환한다
4. WHEN 락 설정 후 만료 시간이 지정되지 않으면 THEN THE Distributed Lock System SHALL 기본 만료 시간을 적용하여 데드락을 방지한다

### Requirement 5

**User Story:** 개발자로서, 각 분산 락 구현의 동작을 검증하고 싶습니다. 그래서 동시성 시나리오를 테스트할 수 있는 테스트 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 테스트가 실행되면 THEN THE Distributed Lock System SHALL Testcontainers를 사용하여 MySQL, PostgreSQL, Redis 컨테이너를 자동으로 시작한다
2. WHEN 동일한 Lock Key로 여러 스레드가 동시에 락 획득을 시도하는 테스트를 실행하면 THEN THE Distributed Lock System SHALL 한 번에 하나의 스레드만 락을 획득함을 검증한다
3. WHEN 락 획득 후 해제하는 테스트를 실행하면 THEN THE Distributed Lock System SHALL 락이 정상적으로 해제되고 다른 스레드가 락을 획득할 수 있음을 검증한다
4. WHEN 락 타임아웃 테스트를 실행하면 THEN THE Distributed Lock System SHALL 지정된 시간 후 락이 자동으로 해제됨을 검증한다

### Requirement 6

**User Story:** 개발자로서, 멀티 DataSource 환경에서 각 데이터 저장소를 독립적으로 사용하고 싶습니다. 그래서 MySQL, PostgreSQL, Redis를 위한 별도의 설정이 필요합니다.

#### Acceptance Criteria

1. WHEN 애플리케이션이 시작되면 THEN THE Distributed Lock System SHALL application.yml에서 MySQL DataSource 설정을 읽어 MySQL 연결을 구성한다
2. WHEN 애플리케이션이 시작되면 THEN THE Distributed Lock System SHALL application.yml에서 PostgreSQL DataSource 설정을 읽어 PostgreSQL 연결을 구성한다
3. WHEN 애플리케이션이 시작되면 THEN THE Distributed Lock System SHALL application.yml에서 Redis 설정을 읽어 Redis 연결을 구성한다
4. WHEN Lock Service가 데이터 저장소에 접근하면 THEN THE Distributed Lock System SHALL 각 서비스에 해당하는 DataSource를 사용하여 격리된 연결을 제공한다

### Requirement 7

**User Story:** 개발자로서, 분산 락 사용 시 발생할 수 있는 예외 상황을 이해하고 싶습니다. 그래서 명확한 에러 처리와 로깅이 필요합니다.

#### Acceptance Criteria

1. WHEN 데이터베이스 연결이 실패하면 THEN THE Distributed Lock System SHALL 명확한 에러 메시지와 함께 예외를 발생시킨다
2. WHEN 락 획득 중 타임아웃이 발생하면 THEN THE Distributed Lock System SHALL 타임아웃 정보를 포함한 예외를 발생시킨다
3. WHEN 락 해제 시 해당 락이 존재하지 않으면 THEN THE Distributed Lock System SHALL 경고 로그를 기록하고 실패를 반환한다
4. WHEN Lock Service에서 예외가 발생하면 THEN THE Distributed Lock System SHALL 스택 트레이스와 함께 상세한 로그를 기록한다

### Requirement 8

**User Story:** 개발자로서, 실제 애플리케이션에서 분산 락을 어떻게 사용하는지 예제를 보고 싶습니다. 그래서 실용적인 사용 예제 코드가 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 예제 코드를 실행하면 THEN THE Distributed Lock System SHALL 재고 감소와 같은 실제 비즈니스 시나리오에서 락을 사용하는 방법을 보여준다
2. WHEN 예제에서 락을 획득하면 THEN THE Distributed Lock System SHALL try-finally 블록을 사용하여 락이 항상 해제됨을 보장한다
3. WHEN 예제에서 락 획득에 실패하면 THEN THE Distributed Lock System SHALL 적절한 재시도 로직이나 에러 처리를 보여준다
4. WHEN 예제 코드가 실행되면 THEN THE Distributed Lock System SHALL 각 락 구현 방식의 성능 특성과 사용 시나리오를 주석으로 설명한다

### Requirement 9

**User Story:** 개발자로서, 애너테이션만으로 간편하게 분산 락을 적용하고 싶습니다. 그래서 @DistributedLock 애너테이션과 AOP 기반 구현이 필요합니다.

#### Acceptance Criteria

1. WHEN 개발자가 메서드에 @DistributedLock 애너테이션을 선언하면 THEN THE Distributed Lock System SHALL AOP를 통해 메서드 실행 전에 락을 획득한다
2. WHEN 애너테이션에 SpEL 표현식으로 락 키를 지정하면 THEN THE Distributed Lock System SHALL 메서드 파라미터를 기반으로 동적 락 키를 생성한다
3. WHEN 애너테이션에 락 타입을 지정하면 THEN THE Distributed Lock System SHALL 해당 타입의 Lock Service를 선택하여 락을 획득한다
4. WHEN 메서드 실행이 완료되거나 예외가 발생하면 THEN THE Distributed Lock System SHALL finally 블록에서 락을 반드시 해제한다
5. WHEN 애너테이션에 재시도 설정이 포함되면 THEN THE Distributed Lock System SHALL 지정된 횟수만큼 락 획득을 재시도한다
