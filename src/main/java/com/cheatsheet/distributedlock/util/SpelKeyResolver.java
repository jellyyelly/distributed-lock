package com.cheatsheet.distributedlock.util;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * SpEL 표현식을 사용하여 동적 락 키를 생성하는 유틸리티 클래스
 */
public class SpelKeyResolver {
    
    private static final ExpressionParser parser = new SpelExpressionParser();
    
    /**
     * SpEL 표현식을 평가하여 락 키를 생성합니다.
     * 
     * @param keyExpression SpEL 표현식 (예: "#productId", "'order:' + #orderId")
     * @param method 호출된 메서드
     * @param args 메서드 인자
     * @return 평가된 락 키
     */
    public static String resolve(String keyExpression, Method method, Object[] args) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            throw new IllegalArgumentException("Lock key expression cannot be null or empty");
        }
        
        // SpEL 표현식이 아닌 경우 그대로 반환
        if (!keyExpression.contains("#") && !keyExpression.contains("'")) {
            return keyExpression;
        }
        
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 메서드 파라미터를 컨텍스트에 추가
        String[] parameterNames = getParameterNames(method);
        for (int i = 0; i < parameterNames.length && i < args.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        
        // SpEL 표현식 평가
        Object value = parser.parseExpression(keyExpression).getValue(context);
        return value != null ? value.toString() : "";
    }
    
    /**
     * 메서드의 파라미터 이름을 가져옵니다.
     * Java 8+ 컴파일 시 -parameters 옵션이 필요합니다.
     * 
     * @param method 메서드
     * @return 파라미터 이름 배열
     */
    private static String[] getParameterNames(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }
}
