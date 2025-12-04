package com.cheatsheet.distributedlock.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpEL 키 리졸버의 JUnit 5 기반 단위 테스트
 * 
 * Property-based test와 함께 사용하여 SpEL 표현식 평가의 정확성을 검증합니다.
 */
@DisplayName("SpEL 키 리졸버 테스트")
class SpelKeyResolverTest {
    
    @Test
    @DisplayName("단일 파라미터 SpEL 표현식 평가")
    void spelExpressionWithSingleParameter() throws Exception {
        // Given: 단일 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {"testValue"};
        
        // When: SpEL 표현식 "#arg0"을 평가
        String result = SpelKeyResolver.resolve("#arg0", method, args);
        
        // Then: 파라미터 값이 그대로 반환되어야 함
        assertThat(result).isEqualTo("testValue");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"product123", "order456", "user789", "session_abc"})
    @DisplayName("다양한 단일 파라미터 값으로 SpEL 표현식 평가")
    void spelExpressionWithVariousSingleParameters(String paramValue) throws Exception {
        // Given: 단일 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {paramValue};
        
        // When: SpEL 표현식 "#arg0"을 평가
        String result = SpelKeyResolver.resolve("#arg0", method, args);
        
        // Then: 파라미터 값이 그대로 반환되어야 함
        assertThat(result).isEqualTo(paramValue);
    }
    
    @Test
    @DisplayName("문자열 연결 SpEL 표현식 평가")
    void spelExpressionWithConcatenation() throws Exception {
        // Given: 두 개의 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("twoParams", String.class, String.class);
        Object[] args = {"product", "123"};
        
        // When: SpEL 표현식으로 문자열 연결
        String result = SpelKeyResolver.resolve("'lock:' + #arg0 + ':' + #arg1", method, args);
        
        // Then: 올바르게 연결된 문자열이 반환되어야 함
        assertThat(result).isEqualTo("lock:product:123");
    }
    
    @ParameterizedTest
    @CsvSource({
        "product, 123, lock:product:123",
        "order, 456, lock:order:456",
        "user, abc, lock:user:abc",
        "session, xyz, lock:session:xyz"
    })
    @DisplayName("다양한 파라미터 조합으로 문자열 연결 SpEL 표현식 평가")
    void spelExpressionWithVariousConcatenations(String prefix, String id, String expected) throws Exception {
        // Given: 두 개의 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("twoParams", String.class, String.class);
        Object[] args = {prefix, id};
        
        // When: SpEL 표현식으로 문자열 연결
        String result = SpelKeyResolver.resolve("'lock:' + #arg0 + ':' + #arg1", method, args);
        
        // Then: 올바르게 연결된 문자열이 반환되어야 함
        assertThat(result).isEqualTo(expected);
    }
    
    @Test
    @DisplayName("숫자 파라미터를 포함한 SpEL 표현식 평가")
    void spelExpressionWithNumericParameter() throws Exception {
        // Given: 문자열과 정수 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("mixedParams", String.class, int.class);
        Object[] args = {"product123", 5};
        
        // When: SpEL 표현식으로 첫 번째 파라미터 참조
        String result = SpelKeyResolver.resolve("#arg0", method, args);
        
        // Then: 첫 번째 파라미터 값이 반환되어야 함
        assertThat(result).isEqualTo("product123");
    }
    
    @Test
    @DisplayName("숫자 파라미터를 문자열로 변환하는 SpEL 표현식 평가")
    void spelExpressionConvertingNumericToString() throws Exception {
        // Given: 문자열과 정수 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("mixedParams", String.class, int.class);
        Object[] args = {"product", 123};
        
        // When: SpEL 표현식으로 숫자를 문자열로 연결
        String result = SpelKeyResolver.resolve("#arg0 + ':' + #arg1", method, args);
        
        // Then: 숫자가 문자열로 변환되어 연결되어야 함
        assertThat(result).isEqualTo("product:123");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"staticKey", "lock:fixed", "constant_value", "simple"})
    @DisplayName("SpEL 표현식이 아닌 일반 문자열 처리")
    void plainStringWithoutSpelExpression(String lockKey) throws Exception {
        // Given: 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {"someValue"};
        
        // When: SpEL 표현식이 아닌 일반 문자열 사용
        String result = SpelKeyResolver.resolve(lockKey, method, args);
        
        // Then: 입력 문자열이 그대로 반환되어야 함
        assertThat(result).isEqualTo(lockKey);
    }
    
    @Test
    @DisplayName("SpEL 표현식 평가의 결정성 검증")
    void spelExpressionDeterminism() throws Exception {
        // Given: 동일한 메서드와 파라미터
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {"testValue"};
        String expression = "#arg0";
        
        // When: 동일한 SpEL 표현식을 여러 번 평가
        String result1 = SpelKeyResolver.resolve(expression, method, args);
        String result2 = SpelKeyResolver.resolve(expression, method, args);
        String result3 = SpelKeyResolver.resolve(expression, method, args);
        
        // Then: 항상 동일한 결과를 반환해야 함 (결정성)
        assertThat(result1).isEqualTo(result2);
        assertThat(result2).isEqualTo(result3);
        assertThat(result1).isEqualTo("testValue");
    }
    
    @Test
    @DisplayName("복잡한 SpEL 표현식 평가")
    void complexSpelExpression() throws Exception {
        // Given: 두 개의 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("twoParams", String.class, String.class);
        Object[] args = {"product", "123"};
        
        // When: 복잡한 SpEL 표현식 평가
        String result = SpelKeyResolver.resolve("'prefix:' + #arg0.toUpperCase() + ':suffix:' + #arg1", method, args);
        
        // Then: 표현식이 올바르게 평가되어야 함
        assertThat(result).isEqualTo("prefix:PRODUCT:suffix:123");
    }
    
    @Test
    @DisplayName("빈 문자열 파라미터 처리")
    void spelExpressionWithEmptyString() throws Exception {
        // Given: 빈 문자열 파라미터
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {""};
        
        // When: SpEL 표현식 평가
        String result = SpelKeyResolver.resolve("#arg0", method, args);
        
        // Then: 빈 문자열이 반환되어야 함
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("null 파라미터 처리")
    void spelExpressionWithNullParameter() throws Exception {
        // Given: null 파라미터
        Method method = TestService.class.getMethod("singleParam", String.class);
        Object[] args = {null};
        
        // When: SpEL 표현식 평가
        String result = SpelKeyResolver.resolve("#arg0", method, args);
        
        // Then: 빈 문자열이 반환되어야 함
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("세 개 이상의 파라미터를 사용한 SpEL 표현식")
    void spelExpressionWithMultipleParameters() throws Exception {
        // Given: 세 개의 파라미터를 받는 메서드
        Method method = TestService.class.getMethod("threeParams", String.class, String.class, String.class);
        Object[] args = {"part1", "part2", "part3"};
        
        // When: 모든 파라미터를 연결하는 SpEL 표현식
        String result = SpelKeyResolver.resolve("#arg0 + ':' + #arg1 + ':' + #arg2", method, args);
        
        // Then: 모든 파라미터가 올바르게 연결되어야 함
        assertThat(result).isEqualTo("part1:part2:part3");
    }
    
    // 테스트용 더미 서비스 클래스
    public static class TestService {
        public void singleParam(String arg0) {}
        public void twoParams(String arg0, String arg1) {}
        public void mixedParams(String arg0, int arg1) {}
        public void threeParams(String arg0, String arg1, String arg2) {}
    }
}
