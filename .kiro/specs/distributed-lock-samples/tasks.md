# Implementation Plan

- [x] 1. 핵심 인터페이스 및 애너테이션 구현
  - @DistributedLock 애너테이션, LockType enum, DistributedLockService 인터페이스 생성
  - 락 관련 예외 클래스 구현 (LockException, LockAcquisitionException 등)
  - 락 결과 및 메타데이터 모델 클래스 구현
  - _Requirements: 9.1, 9.2, 9.3, 7.1, 7.2_

- [x] 1.1 Property test: SpEL 동적 락 키 생성
  - **Property 20: SpEL 동적 락 키 생성**
  - **Validates: Requirements 9.2**

- [x] 2. MySQL 세션 락 서비스 구현
  - MysqlSessionLockService 클래스 구현
  - GET_LOCK 및 RELEASE_LOCK SQL 함수 호출 로직 구현
  - 타임아웃 처리 및 에러 핸들링
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2.1 Property test: MySQL 락 획득 성공 반환
  - **Property 1: MySQL 락 획득 성공 반환**
  - **Validates: Requirements 1.1**

- [x] 2.2 Property test: MySQL 락 해제 성공 반환
  - **Property 2: MySQL 락 해제 성공 반환**
  - **Validates: Requirements 1.2**

- [x] 2.3 Property test: MySQL 락 타임아웃 실패
  - **Property 3: MySQL 락 타임아웃 실패**
  - **Validates: Requirements 1.3**

- [x] 2.4 Property test: MySQL 상호 배제
  - **Property 4: MySQL 상호 배제**
  - **Validates: Requirements 1.4**

- [x] 3. PostgreSQL Advisory Lock 서비스 구현
  - PostgresAdvisoryLockService 클래스 구현
  - pg_try_advisory_lock 함수 호출 로직 구현 (논블로킹 방식)
  - pg_advisory_unlock 함수 호출 로직 구현
  - 문자열 Lock Key를 정수 해시로 변환하는 유틸리티 메서드 구현
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 3.1 Property test: PostgreSQL 논블로킹 락 즉시 반환
  - **Property 6: PostgreSQL 논블로킹 락 즉시 반환**
  - **Validates: Requirements 2.2**

- [x] 3.2 Property test: PostgreSQL 락 해제 성공 반환
  - **Property 7: PostgreSQL 락 해제 성공 반환**
  - **Validates: Requirements 2.3**

- [x] 3.3 Property test: 문자열 키 해시 변환 결정성
  - **Property 8: 문자열 키 해시 변환 결정성**
  - **Validates: Requirements 2.4**

- [x] 4. Redis Lua Script 락 서비스 구현
  - RedisLuaLockService 클래스 구현
  - 락 획득용 Lua 스크립트 작성 및 실행 로직 구현
  - 락 해제용 Lua 스크립트 작성 및 실행 로직 (소유자 검증 포함)
  - 고유 소유자 ID 생성 로직 (UUID 기반)
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 4.1 Property test: Lua 스크립트 원자적 락 획득
  - **Property 9: Lua 스크립트 원자적 락 획득**
  - **Validates: Requirements 3.1**

- [x] 4.2 Property test: Lua 스크립트 소유자 검증 해제
  - **Property 10: Lua 스크립트 소유자 검증 해제**
  - **Validates: Requirements 3.2**

- [x] 4.3 Property test: Redis 상호 배제
  - **Property 11: Redis 상호 배제**
  - **Validates: Requirements 3.3**

- [x] 4.4 Property test: Redis 자동 만료
  - **Property 12: Redis 자동 만료**
  - **Validates: Requirements 3.4**

- [x] 5. Redis SETNX 락 서비스 구현
  - RedisSetnxLockService 클래스 구현
  - SET NX EX 명령을 사용한 락 획득 로직 구현
  - DEL 명령을 사용한 락 해제 로직 구현
  - 기본 만료 시간 설정 로직 (타임아웃이 0이거나 지정되지 않은 경우)
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 5.1 Property test: SETNX 락 획득 및 만료 설정
  - **Property 13: SETNX 락 획득 및 만료 설정**
  - **Validates: Requirements 4.1**

- [x] 5.2 Property test: SETNX 락 해제
  - **Property 14: SETNX 락 해제**
  - **Validates: Requirements 4.2**

- [x] 5.3 Property test: SETNX 기존 키 실패
  - **Property 15: SETNX 기존 키 실패**
  - **Validates: Requirements 4.3**

- [x] 5.4 Property test: SETNX 기본 만료 시간 적용
  - **Property 16: SETNX 기본 만료 시간 적용**
  - **Validates: Requirements 4.4**

- [x] 6. AOP Aspect 구현
  - DistributedLockAspect 클래스 구현
  - @Around advice로 @DistributedLock 메서드 인터셉션
  - SpEL 표현식 파서 및 평가 로직 구현
  - 락 타입에 따른 Lock Service 선택 로직
  - 재시도 로직 구현 (retryCount, retryInterval 지원)
  - try-finally를 통한 락 해제 보장
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 6.1 Property test: AOP 메서드 인터셉션
  - **Property 19: AOP 메서드 인터셉션**
  - **Validates: Requirements 9.1**

- [x] 6.2 Property test: 락 타입 기반 서비스 선택
  - **Property 21: 락 타입 기반 서비스 선택**
  - **Validates: Requirements 9.3**

- [x] 6.3 Property test: 락 해제 보장
  - **Property 22: 락 해제 보장**
  - **Validates: Requirements 9.4**

- [x] 6.4 Property test: 재시도 로직
  - **Property 23: 재시도 로직**
  - **Validates: Requirements 9.5**

- [x] 7. AOP 설정 및 Lock Service 등록
  - DistributedLockConfig 클래스 생성
  - @EnableAspectJAutoProxy 설정
  - 모든 Lock Service를 Map으로 주입하여 Aspect에 전달
  - _Requirements: 9.1_

- [ ] 8. 실용 예제 서비스 구현
  - InventoryService 클래스 구현 (재고 감소 시나리오)
  - @DistributedLock 애너테이션을 사용한 메서드 작성
  - 다양한 락 타입 사용 예제 (MySQL, PostgreSQL, Redis)
  - SpEL 표현식을 사용한 동적 락 키 예제
  - 재시도 설정 예제
  - _Requirements: 8.1, 8.2, 8.3_

- [ ] 9. Checkpoint - 모든 테스트 통과 확인
  - 모든 테스트가 통과하는지 확인하고, 문제가 있으면 사용자에게 질문합니다.

- [ ] 10. 통합 테스트 작성
  - Testcontainers를 사용한 통합 테스트 클래스 작성
  - MySQL, PostgreSQL, Redis 컨테이너 설정
  - 멀티 스레드 동시성 테스트
  - 락 획득-해제 사이클 테스트
  - 자동 만료 동작 테스트
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 10.1 Property test: 락 획득-해제 사이클
  - **Property 17: 락 획득-해제 사이클**
  - **Validates: Requirements 5.3**

- [ ] 10.2 Property test: 존재하지 않는 락 해제 실패
  - **Property 18: 존재하지 않는 락 해제 실패**
  - **Validates: Requirements 7.3**

- [ ] 11. 최종 Checkpoint - 전체 시스템 검증
  - 모든 테스트가 통과하는지 확인하고, 문제가 있으면 사용자에게 질문합니다.
