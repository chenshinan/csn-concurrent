# 并发编程

## synchronized:同步锁

synchronized是一种`悲观锁`，它假设最坏的情况，并且只有在确保其它线程不会造成干扰的情况下执行，会导致其它所有需要锁的线程挂起，等待持有锁的线程释放锁

### synchronized同步锁的应用场景

* 实例方法，锁住的是该类的实例对象

当一个线程访问一个对象实例的同步方法A时，其他线程对该对象实例中所有其它同步方法的访问将被阻塞。因为第一个线程已经获得了对象锁，其他线程得不到锁，则虽然是访问不同的方法，但是没有获得锁，也无法访问

```java
public synchronized void method(){
    ...
}
```

* 静态方法，锁住的是类对象

当一个线程访问一个类的静态同步方法A时，其他线程对该类中所有其它静态同步方法的访问将被阻塞。因为第一个线程已经获得了类锁，其他线程得不到锁

```java
public static synchronized void method(){
    ...
}
```

* 同步代码块，锁住的是该类的实例对象

当一个线程访问一个对象实例的synchronized(this)同步代码块时，它就获得了这个对象实例的对象锁。其它线程对该对象实例的所有同步代码部分的访问都被暂时阻塞

`使用同步代码块来替代同步方法减少同步的时间`

```java
synchronized(this){
    ...
}
```

* 同步代码块，锁住的是该类的类对象

当一个线程访问一个对象实例的synchronized(XX.class)同步代码块时，它就获得了这个类的类锁。其它线程对该类的所有同步代码部分的访问都被暂时阻塞

```java
synchronized(Issue.class){
    ...
}
```

* 同步代码块，锁住的是配置的实例对象

```java
String str = 'test';
synchronized(str){
    ...
}
```

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

## AQS:AbstractQueuedSynchronizer（抽象队列同步器）

AQS 是很多同步器的基础框架，比如 ReentrantLock、CountDownLatch 和 Semaphore 等都是基于 AQS 实现的

### 实现原理：

在 AQS 内部，通过维护一个FIFO 队列来管理多线程的排队工作。在公平竞争的情况下，无法获取同步状态的线程将会被封装成一个节点，置于队列尾部。入队的线程将会通过自旋的方式获取同步状态，若在有限次的尝试后，仍未获取成功，线程则会被阻塞住。当头结点释放同步状态后，且后继节点对应的线程被阻塞，此时头结点线程将会去唤醒后继节点线程。后继节点线程恢复运行并获取同步状态后，会将旧的头结点从队列中移除，并将自己设为头结点

### 节点的等待状态：waitStatus

----------------
|waitStatus|说明|
|-------|----|
|CANCELLED(1)|当前线程因为超时或者中断被取消。这是一个终结态，也就是状态到此为止|
|SIGNAL(-1)|当前线程的后继线程被阻塞或者即将被阻塞，当前线程释放锁或者取消后需要唤醒后继线程，这个状态一般都是后继线程来设置前驱节点的|
|CONDITION(-2)|当前线程在condition队列中|
|PROPAGATE(-3)|用于将唤醒后继线程传递下去，这个状态的引入是为了完善和增强共享锁的唤醒机制。在一个节点成为头节点之前，是不会跃迁为此状态的|
|0|表示无状态，后续节点正在运行中|

### 独占模式：获取锁

* 调用`acquire`，内部调用`tryAcquire（自定义覆盖实现的）`方法尝试获取同步状态

* -获取成功，直接返回

* -获取失败，将线程封装到节点中，并调用`acquireQueued`将节点入队

* 入队节点在`acquireQueued`方法中自旋获取同步状态

* 若节点的前驱节点是头节点，则再次调用`tryAcquire`尝试获取同步状态

* -获取成功，当前节点将自己设为头节点并返回

* -获取失败，进入下一步判断

* 获取同步状态失败，则会调用`shouldParkAfterFailedAcquire`判断是否应该阻塞自己，如果不阻塞，CPU就会处于忙等状态，这样会浪费CPU资源

* `shouldParkAfterFailedAcquire`中会根据前驱节点的waitStatus的值，决定后续的动作

* -前驱节点等待状态为`SIGNAL(-1)`，表示当前线程应该被阻塞，调用`parkAndCheckInterrupt`中的`LockSupport.park(this)`阻塞自己，线程阻塞后，会在前驱节点释放同步状态后被前驱节点线程唤醒

* -前驱节点等待状态为`CANCELLED(1)`，则以前驱节点为起点向前遍历，移除其他等待状态为 CANCELLED 的节点，继续自旋获取同步状态

* -否则前驱节点等待状态为`0 或 PROPAGATE(-3)`，则设置前驱节点为 SIGNAL，继续自旋获取同步状态

* 如果在获取同步状态中出现异常`（tryAcquire 需同步组件开发者覆写，难免不了会出现异常）`，则会调用`cancelAcquire`取消获取同步状态

* `cancelAcquire`中，设置当前节点为 CANCELLED，继续唤醒后续节点`unparkSuccessor(node)`

* `unparkSuccessor`中通过CAS将当前节点设置为 0，以便后续节点多一次尝试获取同步状态的机会，唤醒后续节点线程`LockSupport.unpark(s.thread)`

![List的架构图](https://images2015.cnblogs.com/blog/584724/201706/584724-20170612211300368-774544064.png)

### 独占模式：释放锁

* 调用`release`，内部调用`tryRelease（自定义覆盖实现的）`方法尝试释放同步状态

* -获取失败，返回false

* -获取成功，判断当前队列中头节点的值，进行相应操作

* --`head=null`，还未初始化完，不需要唤醒

* --`head!=null && waitStatus=0`，表示后续节点还在运行中，不需要唤醒

* --`head!=null && waitStatus<0`，表示后续线程可能被阻塞，需要唤醒，调用`unparkSuccessor(h)`

### CountDownLatch

CountDownLatch是一个同步工具类，用来协调多个线程之间的同步，能够使一个线程在等待另外一些线程完成各自工作之后，再继续执行。使用一个计数器进行实现。计数器初始值为线程的数量。当每一个线程完成自己任务后，计数器的值就会减一。当计数器的值为0时，表示所有的线程都已经完成了任务，然后在CountDownLatch上等待的线程就可以恢复执行任务