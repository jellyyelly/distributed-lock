package com.cheatsheet.distributedlock.exception;

/**
 * 데이터베이스 또는 캐시 서버 연결 실패 시 발생하는 예외
 */
public class LockConnectionException extends LockException {
    
    private final String dataSource;
    
    public LockConnectionException(String dataSource, String lockKey, Throwable cause) {
        super(String.format("Failed to connect to %s: %s", dataSource, cause.getMessage()), 
                lockKey, LockOperation.ACQUIRE, cause);
        this.dataSource = dataSource;
    }
    
    public String getDataSource() {
        return dataSource;
    }
}
