package com.cheetsheet.distributedlock.exception;

/**
 * 락 획득 실패 시 발생하는 예외
 */
public class LockAcquisitionException extends LockException {
    
    public LockAcquisitionException(String message, String lockKey) {
        super(message, lockKey, LockOperation.ACQUIRE);
    }
    
    public LockAcquisitionException(String message, String lockKey, Throwable cause) {
        super(message, lockKey, LockOperation.ACQUIRE, cause);
    }
}
