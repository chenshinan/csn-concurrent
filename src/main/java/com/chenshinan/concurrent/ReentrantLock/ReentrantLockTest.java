package com.chenshinan.concurrent.ReentrantLock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author shinan.chen
 * @since 2018/11/22
 */
public class ReentrantLockTest {
    public static void main(String[] args) {
        final ReentrantLock reentrantLock = new ReentrantLock();
        final Condition conditionA = reentrantLock.newCondition();
        final Condition conditionB = reentrantLock.newCondition();

//        Thread thread1 = new Thread(Runnable()->{},"thread1");
    }
}
