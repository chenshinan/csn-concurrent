package com.chenshinan.concurrent.AQS;

/**
 * @author shinan.chen
 * @since 2018/11/17
 */
public abstract class MyAbstractOwnableSynchronizer {

    protected MyAbstractOwnableSynchronizer() { }

    /**
     * 当前独占线程
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * 设置独占线程
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * 获取独占线程
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
