package com.chenshinan.concurrent.threadLocal;

/**
 * @author shinan.chen
 * @since 2019/10/8
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 此类提供线程局部变量。这些变量与正常变量不同，因为每个访问一个线程（通过其{@code get}或{@code set}方法）的线程都有其自己的，
 * 独立初始化的变量副本。 {@code ThreadLocal}实例通常是希望将状态与线程相关联的类中的私有静态字段（例如用户ID或交易ID）
 * <p>
 * 只要线程是活动的，并且可以访问{@code ThreadLocal}实例，则每个线程都对其线程局部变量的副本持有隐式引用。
 * 线程消失后，所有其线程本地实例的副本都将进行垃圾回收（除非存在对这些副本的其他引用）
 */
public class ThreadLocal<T> {
    /**
     * ThreadLocals依赖于每个线程（Thread.threadLocals和 InheritableThreadLocals）附加的每个线程的线性探针哈希映射。
     * ThreadLocal对象充当键，通过threadLocalHashCode搜索。这是一个自定义哈希码（仅在ThreadLocalMaps中有用），
     * 在相同的线程使用连续构造的ThreadLocals 的情况下，消除了冲突
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * 下一个hashcode
     */
    private static AtomicInteger nextHashCode =
            new AtomicInteger();

    /**
     * 连续生成的哈希码之间的区别
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * 返回下一个hashcode
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * 线程初始值
     */
    protected T initialValue() {
        return null;
    }

    /**
     * 返回此线程局部变量的当前线程副本中的值。如果变量没有当前线程的值，则首先通过调用initialValue方法将其初始化为返回的值
     */
    public T get() {
        Thread t = Thread.currentThread();
        // 首先会获取当前线程的ThreadLocalMap，如果为空则初始化一个
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T) e.value;
                return result;
            }
        }
        return setInitialValue();
    }

    /**
     * 首先会获取当前线程的ThreadLocalMap，如果存在则覆盖，否则createMap
     */
    private T setInitialValue() {
        // 默认初始值为null
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    /**
     * 将此线程局部变量的当前线程副本设置为指定值
     */
    public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }

    /**
     * 删除此线程局部变量的当前线程值
     */
    public void remove() {
        ThreadLocal.ThreadLocalMap m = getMap(Thread.currentThread());
        if (m != null)
            m.remove(this);
    }

    /**
     * 获取当前线程的ThreadLocalMap
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * 创建当前线程的ThreadLocalMap，并设置firstValue
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * ThreadLocalMap是自定义的哈希映射，仅适用于维护线程局部值
     */
    static class ThreadLocalMap {

        /**
         * ThreadLocalMap内部包含Entry的数组，Entry是持有ThreadLocal的弱引用
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /**
             * 与此ThreadLocal关联的值
             */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * 初始化table的大小
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * Entry数组table，必须是2的幂次
         */
        private ThreadLocalMap.Entry[] table;

        /**
         * 表中的条目数
         */
        private int size = 0;

        /**
         * 要调整大小的下一个大小值
         */
        private int threshold; // Default to 0

        /**
         * 设置调整大小阈值以保持最坏的2/3负载系数
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * 构造一个最初包含（firstKey，firstValue）的新Map。
         * ThreadLocalMaps是惰性构造的，因此只有在至少要放置一个条目时才创建
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            // 初始化table数组
            table = new ThreadLocalMap.Entry[INITIAL_CAPACITY];
            // 用firstKey的threadLocalHashCode与初始大小16取模得到哈希值
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            // 初始化该节点
            table[i] = new ThreadLocalMap.Entry(firstKey, firstValue);
            // 设置节点表大小为1
            size = 1;
            // 设定扩容阈值
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * 获取与键关联的条目
         */
        private ThreadLocalMap.Entry getEntry(ThreadLocal<?> key) {
            // 根据key这个ThreadLocal的ID来获取索引，也即哈希值
            int i = key.threadLocalHashCode & (table.length - 1);
            // 对应的entry存在且未失效且弱引用指向的ThreadLocal就是key，则命中返回
            ThreadLocalMap.Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * 当在直接哈希槽中找不到key时使用的getEntryAfterMiss()，内部调用expungeStaleEntry清除"脏entry"
         * 脏entry的生产是由于在ThreadLocal实例再被回收之后，线程内部Entry的key是弱引用，所以被回收，而value是强引用，所以无法被回收
         */
        private ThreadLocal.ThreadLocalMap.Entry getEntryAfterMiss(ThreadLocal<?> key, int i, ThreadLocal.ThreadLocalMap.Entry e) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                // 如果key过期则清理数据，否则环形查找下一个
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        /**
         * 将此线程局部变量的当前线程副本设置为指定值
         */
        private void set(ThreadLocal<?> key, Object value) {

            ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            // 根据ThreadLocal的散列值，查找对应元素在数组中的位置
            int i = key.threadLocalHashCode & (len - 1);

            // 使用线性探测法查找元素，通过环型查找，直到找到e==null的情况或覆盖
            for (ThreadLocalMap.Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();

                // ThreadLocal存在时，直接覆盖之前的值
                if (k == key) {
                    e.value = value;
                    return;
                }
                // key为null，但是值不为null，说明之前的ThreadLocal对象已经被回收了，当前数组中的Entry是
                if (k == null) {
                    // 用新元素代替陈旧的元素，并cleanSomeSlots()
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }
            // 当前e=null，则new一个新的entry
            tab[i] = new ThreadLocalMap.Entry(key, value);
            int sz = ++size;
            // 如果数量达到域值，则扩容
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * 用新元素代替陈旧的元素，并cleanSomeSlots()
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            ThreadLocal.ThreadLocalMap.Entry e;

            // 记录要清除的插槽
            int slotToExpunge = staleSlot;
            // 向前扫描，查找最前的一个无效slot
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;

            // 向后遍历table
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // 如果在向后环形查找过程中发现key相同的entry就覆盖并且和脏entry进行交换
                if (k == key) {
                    e.value = value;
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    /*
                     * 如果在整个扫描过程中（包括函数一开始的向前扫描与i之前的向后扫描）
                     * 找到了之前的无效slot则以那个位置作为清理的起点，
                     * 否则则以当前的i作为清理起点
                     */
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    // 从slotToExpunge开始做一次连续段的清理，再做一次启发式清理
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // 如果当前的slot已经无效，并且向前扫描过程中没有无效slot，则更新slotToExpunge为当前位置
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // 如果key在table中不存在，则在原地放一个即可
            tab[staleSlot].value = null;
            tab[staleSlot] = new ThreadLocal.ThreadLocalMap.Entry(key, value);

            // 在探测过程中如果发现任何无效slot，则做一次清理（连续段清理+启发式清理）
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * 通过重新散列位于staleSlot和下一个null插槽之间的任何可能冲突的条目
         * 来消除陈旧的条目。这还会删除在结尾的null之前遇到的所有其他过时的条目
         */
        private int expungeStaleEntry(int staleSlot) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;

            // 清理脏entry
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // 往后查找entry，直到null。如果遇到过时则清理，有值的重新哈希到合适位置
            ThreadLocal.ThreadLocalMap.Entry e;
            int i;
            for (i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {
                        tab[i] = null;
                        // 找到空槽，赋予当前entry
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * 启发式地清理slot,
         * i对应entry是非无效（指向的ThreadLocal没被回收，或者entry本身为空）
         * n是用于控制控制扫描次数的
         * 正常情况下如果log n次扫描没有发现无效slot，函数就结束了
         * 但是如果发现了无效的slot，将n置为table的长度len，做一次连续段的清理
         * 再从下一个空的slot开始继续扫描
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            do {
                // i在任何情况下自己都不会是一个无效slot，所以从下一个开始判断
                i = nextIndex(i, len);
                ThreadLocal.ThreadLocalMap.Entry e = tab[i];
                if (e != null && e.get() == null) {
                    // 扩大扫描控制因子
                    n = len;
                    removed = true;
                    // 清理一个连续段
                    i = expungeStaleEntry(i);
                }
            } while ((n >>>= 1) != 0);
            return removed;
        }

        /**
         * 重新包装和/或调整桌子大小。首先扫描整个*表，删除陈旧的条目。如果这还不足以*缩小表的大小，则将表的大小加倍。
         */
        private void rehash() {
            expungeStaleEntries();

            // 使用较低的阈值加倍以避免滞后
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * 将表的容量加倍
         */
        private void resize() {
            ThreadLocal.ThreadLocalMap.Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            ThreadLocal.ThreadLocalMap.Entry[] newTab = new ThreadLocal.ThreadLocalMap.Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                ThreadLocal.ThreadLocalMap.Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * 清除表中所有过时的条目
         */
        private void expungeStaleEntries() {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                ThreadLocal.ThreadLocalMap.Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }

        /**
         * 从map中删除ThreadLocal
         */
        private void remove(ThreadLocal<?> key) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);
            for (ThreadLocal.ThreadLocalMap.Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    // 显式断开弱引用
                    e.clear();
                    // 进行段清理
                    expungeStaleEntry(i);
                    return;
                }
            }
        }
    }

}
