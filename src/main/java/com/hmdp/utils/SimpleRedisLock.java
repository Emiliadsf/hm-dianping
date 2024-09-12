package com.hmdp.utils;

public class SimpleRedisLock implements ILock {


    @Override
    public boolean trtLock(long timeoutSec) {
        return false;
    }

    @Override
    public void unLock() {

    }
}
