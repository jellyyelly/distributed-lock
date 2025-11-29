package com.cheatsheet.distributedlock.exception;

/**
 * 락 획득 타임아웃 시 발생하는 예외
 */
public class LockTimeoutException extends LockAcquisitionException {
    
    private final int timeoutSeconds;
    
    public LockTimeoutException(String lockKey, int timeoutSeconds) {
        super(String.format("Lock acquisition timed out for key '%s' after %d seconds", 
                lockKey, timeoutSeconds), lockKey);
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
