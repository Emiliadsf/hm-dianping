package com.hmdp.utils;

public interface ILock {

    boolean trtLock(long timeoutSec);


    void unLock();

    }

