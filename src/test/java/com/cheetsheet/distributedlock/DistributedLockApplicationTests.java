package com.cheetsheet.distributedlock;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 전체 애플리케이션 컨텍스트 로드 테스트
 * 모든 데이터베이스 컨테이너가 필요하므로 기본적으로 비활성화
 */
@Disabled("전체 컨텍스트 로드 테스트는 CI/CD에서만 실행")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DistributedLockApplicationTests {

    @Test
    void contextLoads() {
    }

}
