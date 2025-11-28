package com.cheetsheet.distributedlock.exception;

/**
 * 락 작업 중 발생하는 예외를 나타내는 커스텀 예외
 */
public class LockException extends RuntimeException {
    
    private final String lockKey;
    private final LockOperation operation;
    
    public enum LockOperation {
        ACQUIRE, RELEASE, TIMEOUT
    }
    
    public LockException(String message, String lockKey, LockOperation operation) {
        super(message);
        this.lockKey = lockKey;
        this.operation = operation;
    }
    
    public LockException(String message, String lockKey, LockOperation operation, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
        this.operation = operation;
    }
    
    public String getLockKey() {
        return lockKey;
    }
    
    public LockOperation getOperation() {
        return operation;
    }
}
