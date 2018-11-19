package com.chenshinan.concurrent.AQS;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author shinan.chen
 * @since 2018/11/16
 */
public class MyCountDownLatch {
    /**
     * 内部类Sync
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (; ; ) {
                int c = getState();
                if (c == 0) {
                    return false;
                }
                int nextc = c - 1;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0;
                }
            }
        }
    }

    private final MyCountDownLatch.Sync sync;

    public MyCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.sync = new MyCountDownLatch.Sync(count);
    }

    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 减少锁存器的计数，如果计数达到零，则释放所有等待的线程
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    /**
     * 获取当前计数
     */
    public long getCount() {
        return sync.getCount();
    }

    @Override
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
