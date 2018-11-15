# 并发编程

## CAS:Compare and Swap

CAS操作是一种`乐观锁`，悲观锁由于在进程挂起和恢复执行过程中存在着很大的开销，因此产生乐观锁，乐观锁每次不加锁而是假设没有冲突而去完成某项操作，如果因为冲突失败就重试，直到成功为止

> 在线程冲突较少的情况下，CAS的性能比synchronized好；而线程冲突严重的情况下，synchronized性能远高于CAS

### Atomic包

Java从JDK1.5开始提供了java.util.concurrent.atomic包，方便程序员在多线程环境下，无锁的进行原子操作。原子变量的底层使用了处理器提供的原子指令，但是不同的CPU架构可能提供的原子指令不一样，也有可能需要某种形式的内部锁,所以该方法不能绝对保证线程不被阻塞

    AtomicInteger：原子更新整型
    AtomicIntegerArray：原子更新整型数组里的元素
    AtomicReference：原子更新引用类型
    AtomicReferenceFieldUpdater：原子更新引用类型里的字段
    等...

* AtomicInteger源码

```java
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

/**
 * 当执行增加操作时，采用cas循环，每次执行+1时，都会进入到unsafe.compareAndSwapInt
 * 方法中进行比较，若A线程获取到current后，B线程已经+1，此时A线程的current就是旧的值，
 * 则需要循环获取新的current，以此来保证每个线程的+1都会正常执行
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

/**
 * 调用unsafe的native方法，根据value的内存地址直接进行比较与修改
 */
public final Boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}
```

* CAS的缺陷：ABA问题

因为CAS是基于内存共享机制实现的，比如在AtomicInteger类中使用了关键字`volatile`修饰的属性： private volatile int value;

    线程1在共享变量中读到值为A
    线程1被抢占了，线程2执行
    线程2把共享变量里的值从A改成了B，再改回到A，此时被线程1抢占。
    线程1回来看到共享变量里的值没有被改变，于是继续执行。
    虽然线程t1以为变量值没有改变，继续执行了，但是这个过程中(即A的值被t2改变期间)会引发一些潜在的问题

`因为CAS判断的是指针的地址。如果这个地址被重用了呢，问题就很大了。（地址被重用是很经常发生的，一个内存分配后释放了，再分配，很有可能还是原来的地址）`

简单解决方案：不是更新某个引用的值，而是更新两个值，包括一个引用和一个版本号，即使这个值由A变为B，然后为变为A，版本号也是不同的。AtomicStampedReference和AtomicMarkableReference支持在两个变量上执行原子的条件更新。AtomicStampedReference更新一个“对象-引用”二元组，通过在引用上加上“版本号”，从而避免ABA问题

参考文献：
AtomicInteger源码分析——基于CAS的乐观锁实现：https://blog.csdn.net/qfycc92/article/details/46489553
基于CAS思想的java并发AtomicBoolean实例详解：https://www.jianshu.com/p/eabecdbc6bd9
Unsafe类相关：http://aswang.iteye.com/blog/1741871