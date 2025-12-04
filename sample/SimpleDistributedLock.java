package com.inet.api.common.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SimpleDistributedLock implements AutoCloseable {

    private final String lockKey;
    private final String lockValue;
    private final long ttl;
    private final LuaScriptLockManager lockManager;

    @Override
    public void close() throws Exception {
        if (lockManager != null) {
            lockManager.releaseLock(this);
        }
    }
}
