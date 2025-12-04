package com.cheatsheet.distributedlock.exception;

/**
 * 존재하지 않는 락을 해제하려고 할 때 발생하는 예외
 */
public class LockNotHeldException extends LockReleaseException {
    
    public LockNotHeldException(String lockKey) {
        super(String.format("Lock '%s' is not currently held", lockKey), lockKey);
    }
}
