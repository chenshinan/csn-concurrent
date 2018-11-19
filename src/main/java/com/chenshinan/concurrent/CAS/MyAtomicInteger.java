package com.chenshinan.concurrent.CAS;

import sun.misc.Unsafe;

import java.util.concurrent.CountDownLatch;

/**
 * @author shinan.chen
 * @since 2018/11/15
 */
public class MyAtomicInteger {

    /**
     * Unsafe类提供了硬件级别的原子操作,native本地方法
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * 用来存储当前实例中value字段的内存地址
     */
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset(MyAtomicInteger.class.getDeclaredField("value"));
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private volatile int value;

    public final int get() {
        return value;
    }

    public final void set(int newValue) {
        value = newValue;
    }

    /**
     * 当执行增加操作时，采用cas循环，每次执行+1时，都会进入到unsafe.compareAndSwapInt
     * 方法中进行比较，若A线程获取到current后，B线程已经+1，此时A线程的current就是旧的值，
     * 则需要循环获取新的current，以此来保证每个线程的+1都会正常执行
     *
     * @return
     */
    public final int getAndIncrement() {
        for (; ; ) {
            int current = get();
            int next = current + 1;
            if (compareAndSet(current, next)) {
                return current;
            }
        }
    }

    public final int getAndDecrement() {
        while (true) {
            int current = get();
            int last = current - 1;
            if (compareAndSet(current, last)) {
                return current;
            }
        }
    }

    /**
     * 调用unsafe的native方法，根据value的内存地址直接进行比较与修改
     *
     * @param expect
     * @param update
     * @return
     */
    public final Boolean compareAndSet(int expect, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }
}
