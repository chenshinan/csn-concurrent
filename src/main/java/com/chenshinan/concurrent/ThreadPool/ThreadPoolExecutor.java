package com.chenshinan.concurrent.ThreadPool;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * ctl是线程池的主要控制状态，它包含了两个字段：
     * workerCount：当前有效线程数
     * runState：运行状态
     *
     * 为了用一个int来表示两个字段，我们规定workerCount用 (2^29)-1 表示，runState用 (2^31)-1 表示
     *
     * workerCount是已被允许启动但未被允许停止的workers数量，该数可能与实际运行的线程数不相同
     *
     * runState提供主要的生命周期控制：
     *   RUNNING:  接受新任务并处理排队的任务
     *   SHUTDOWN: 不接受新任务，而是处理排队的任务
     *   STOP:     不接受新任务，不处理排队任务，中断进行中的任务
     *   TIDYING:  所有任务都已终止，workerCount为0，线程转换为状态TIDYING 将运行Terminated()挂钩方法
     *   TERMINATED: 调用Terminated()完成
     *
     * runState随时间而改变，转换顺序如下:
     *
     * RUNNING -> SHUTDOWN
     *    在调用shutdown()时，可能隐式在finalize()中
     * (RUNNING or SHUTDOWN) -> STOP
     *    在调用shutdownNow()时
     * SHUTDOWN -> TIDYING
     *    当等待队列和工作者集合为空时
     * STOP -> TIDYING
     *    当工作者集合为空时
     * TIDYING -> TERMINATED
     *    当Terminate()挂钩方法完成时
     *
     * 检测到从SHUTDOWN到TIDYING的转换比您想要的要简单得多，
     * 因为在SHUTDOWN状态期间队列在非空之后可能变为空，反之亦然
     * 但是我们只有在看到队列为空后才能终止，看到workerCount为0.
     *
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState 存在高位，所以左移
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // 打包ctl、解析ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }
    /**
     * 通过CAS增加workerCount
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }
    /**
     * 通过CAS减少workerCount
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }
    /**
     * 用于保留任务并移交给工作线程的队列
     */
    private final BlockingQueue<Runnable> workQueue;
    /**
     * 线程池的可重入锁，用来控制线程的执行与中断
     */
    private final ReentrantLock mainLock = new ReentrantLock();
    /**
     * 线程池中的线程工作者的集合，持有mainLock才可以访问
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();
    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();
    /**
     * 线程池中达到的最大线程数，持有mainLock才可以访问
     */
    private int largestPoolSize;
    /**
     * 计数完成的任务数。仅在工作线程终止时更新。持有mainLock才可以访问
     */
    private long completedTaskCount;
    /*
     * 多线程共用的变量，要声明为volatile
     */
    /**
     * 用于创建线程的工厂
     */
    private volatile ThreadFactory threadFactory;
    /**
     * 拒绝处理器，用于队列满了或者shutdown之后，会拒绝新的线程工作者
     */
    private volatile RejectedExecutionHandler handler;
    /**
     * 超出corePoolSize，并且小于maximumPoolSize的线程启动后，如果空闲时间超过keepAliveTime，则会被回收
     */
    private volatile long keepAliveTime;
    /**
     * 是否允许核心线程也有存活时间，默认false
     */
    private volatile boolean allowCoreThreadTimeOut;
    /**
     * 核心线程数是线程池会保持线程存活的最大数
     */
    private volatile int corePoolSize;
    /**
     * 线程最大容量数是线程池最多拥有的线程数
     */
    private volatile int maximumPoolSize;
    /**
     * 线程池 默认拒绝处理器
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();
    /**
     * 调用shutdown和shutdownNow所需的权限
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /* 执行终结器时使用的上下文，或者为null */
    private final AccessControlContext acc;

    private static final boolean ONLY_ONE = true;

    /**
     * Class Worker主要维护线程运行任务的中断控制状态，以及其他一些记录
     * 该类通过继承AQS简化了对锁的操作
     */
    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        private static final long serialVersionUID = 6138294804551838833L;

        /** 当前工作者的线程 */
        final Thread thread;
        /** 工作者的第一个任务，可以为空 */
        Runnable firstTask;
        /** 当前工作者完成的任务计数器 */
        volatile long completedTasks;

        Worker(Runnable firstTask) {
            // new出来后，设置state=-1禁止中断，直到runWorker()
            setState(-1);
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /** 将主运行循环委托给外部runWorker  */
        public void run() {
            runWorker(this);
        }

        // state=0代表解锁状态
        // state=0代表加锁状态

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }
        /**
         * 如果state>=0，则强制中断正在执行的线程
         */
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /**
     * 主工作者运行循环。反复从队列中获取任务并执行它们
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 开始之前会先解锁，开始的时候再加锁，这中间就可以被中断
        w.unlock();
        // 是否异常退出循环
        boolean completedAbruptly = true;
        try {
            // 开始循环执行任务，如果有firstTask会先执行，没有则getTask()
            while (task != null || (task = getTask()) != null) {
                // 获得一个任务后，给内部AQS加锁
                w.lock();
                // 如果线程池正在停止，并且当前线程未被打断，则中断当前线程
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    // 开始执行之前需要做的事（目前为空，留给子类实现）
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 执行task的内容
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        // 完成执行之前需要做的事（目前为空，留给子类实现）
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    // 完成后给内部AQS解锁
                    w.unlock();
                }
            }
            // 没有异常
            completedAbruptly = false;
        } finally {
            // 执行到这里说明getTask()返回null，说明当前线程池中不需要那么多线程来执行任务了，可以把多于corePoolSize数量的工作线程干掉
            processWorkerExit(w, completedAbruptly);
        }
    }

    /**
     * 循环到等待队列中获取任务
     */
    private Runnable getTask() {
        // 拉取任务是否超时
        boolean timedOut = false;

        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // 如果runStatus=shutdown并且等待队列为空，则退出循环
            // 如果runStatus=STOP，则退出循环
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }
            // 当前工作者数量
            int wc = workerCountOf(c);

            // 是否做线程超时存活判断
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            // 如果工作者数量大于maximumPoolSize，则退出循环
            // 如果当前线程做超时判断并且超时了，workCount>1，且等待队列为空
            // 则减少workCount，并返回空任务，退出循环
            if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // 如果当前工作者做会做线程超时判断，则去等待队列拉取任务，等待keepAliveTime时间
                // 如果当前工作者做不做线程超时判断，则去等待队列拉取任务，无限期等待，直到有任务
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * 处理工作者的退出
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果工作者由于异常退出，则手动减少workCount
        if (completedAbruptly)
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        // 操作工作者集合，需要锁
        mainLock.lock();
        try {
            // 计算线程池完成任务数，并移除工作者
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        // 尝试转换状态到TERMINATED
        tryTerminate();

        int c = ctl.get();
        // 线程池状态处于运行或shutdown时【todo】
        if (runStateLessThan(c, STOP)) {
            // 工作者正常完成任务
            // 如果允许核心线程回收的话，当workQueue非空时，保留一个worker
            // 如果不允许核心线程回收的话，如果workerCount>corePoolSize，则返回，否则，保留corePoolSize个worker
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            addWorker(null, false);
        }
    }

    /**
     * 构造函数
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * 执行一个线程任务，该任务可能被新线程执行或者已经存在的线程执行
     * 如果任务无法提交执行，可能因为线程池已经shutdown或者线程池已经满了
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();

        // 若workCount小于corePoolSize，则添加该任务的工作者（若成功则返回，失败进入下一步）
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        // 若线程正在执行，则把任务加到等待队列中
        if (isRunning(c) && workQueue.offer(command)) {
            // 重新获取当前状态进行判断
            // 如果线程池非运行状态了，则移除等待队列中的任务，并tryTerminate，移除成功后调用拒绝策略
            // 如果线程池在运行，并且workerCount=0，则创建一个空的工作者
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        // 如果加入队列失败，则创建工作者去执行，用非核心线程，否则拒绝
        else if (!addWorker(command, false))
            reject(command);
    }

    /**
     * 检查是否可以针对当前池状态和给定的界限（核心或最大值）添加新的工作者
     * 如果是这样，则将调整workCount计数，并在可能的情况下创建并启动一个新的工作者，并将firstTask作为其第一个任务运行。
     * 如果线程池池已停止或可以关闭，则此方法返回false。
     * 如果在询问时线程工厂无法创建线程，它也会返回false。
     * 如果线程创建失败（由于线程工厂返回null或由于异常（通常是Thread.start（）中的OutOfMemoryError）），
     * 我们将进行干净的回滚
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // 判断线程池状态是否SHUTDOWN，返回false
            if (rs >= SHUTDOWN &&
                    ! (rs == SHUTDOWN &&
                            firstTask == null &&
                            ! workQueue.isEmpty()))
                return false;

            for (;;) {
                // 判断线程池工作线程数是否超过容量，返回false
                int wc = workerCountOf(c);
                if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                // CAS增加workCount，成功跳出最外层循环
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                // CAS失败，重读ctl，如果线程池状态改变则，重复外层循环，直到CAS成功
                c = ctl.get();
                if (runStateOf(c) != rs)
                    continue retry;
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                // 获得线程池可重入锁，加锁，操作工作集合需要加锁
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // 重新校验线程池状态，避免获取锁后线程池被shutdown
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        // 预检查t是否可启动
                        if (t.isAlive())
                            throw new IllegalThreadStateException();
                        // 加入线程池工作集合，更新largestPoolSize，workerAdded=true
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                // 如果工作者成功加入工作集合，则启动该工作者的线程
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            // 如果工作者未启动，则执行失败处理
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * 添加工作者失败，执行回滚：
     * 移除工作集合中的该工作者，CAS来减少workCount，重新检查是否终止，以防此工作者的存在阻止了终止
     * 尝试转换状态到TERMINATED
     */
    private void addWorkerFailed(Worker w) {
        //  获得线程池可重入锁，加锁，操作工作集合需要加锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 如果执行器存在该任务，则将其从执行器的内部队列中移除
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * 尝试转换状态到TERMINATED，当线程池SHUTDOWN，则中断可能正在等待任务的线程（一个）
     * 必须在可能导致终止的任何操作之后调用此方法，以减少关闭状态下的工作人员计数或从队列中删除任务
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            // 线程池还在运行 或 线程池已经终止 或 线程池SHUTDOWN，工作列表不为空时，放弃转换
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            // 如果workerCount不为0，则中断可能正在等待任务的线程（一个）
            if (workerCountOf(c) != 0) {
                interruptIdleWorkers(ONLY_ONE);
                return;
            }
            // 如果workerCount=0，获取锁，CAS更新ctl为终止
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 线程池终止时执行的动作，现在为空
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        // 释放所有锁
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // CAS失败则重复循环
        }
    }

    /**
     * 中断可能正在等待任务的线程，以便检查终止或配置更改
     * 注意，此处忽略无法获得到锁的线程，这些线程后续完成后会自己退出
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // 尝试获取工作者内部AQS锁，如果获取到则可以中断，否则忽略等它执行完
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 主要用于ScheduledThreadPoolExecutor来取消延迟任务
     */
    void onShutdown() {
    }

    /**
     * 启动有序关闭，在该关闭中执行先前提交的任务，但不接受任何新任务
     */
    public void shutdown() {
        // 获取线程池的锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 如果有安全管理器，请确保调用者具有权限关闭线程
            checkShutdownAccess();
            // 通过CAS更新runState为SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 中断所有工作者
            interruptIdleWorkers();
            // 【todo】
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        // 尝试转换状态到TERMINATED
        tryTerminate();
    }

    /**
     * 如果有安全管理器，请确保调用者具有权限，通常可以关闭线程
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * 将runState转换为给定目标，如果已经至少为给定目标，则将其保留
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * 中断所有工作者
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    /**
     * 尝试停止所有正在执行的任务，暂停正在等待的任务的处理，并返回正在等待执行的任务的列表
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            // 通过CAS更新runState为STOP
            advanceRunState(STOP);
            // 强制中断所有线程，即使处于活动状态也是如此
            interruptWorkers();
            // 获取等待队列的所有任务
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        // 尝试转换状态到TERMINATED
        tryTerminate();
        return tasks;
    }

    /**
     * 强制中断所有线程，即使处于活动状态也是如此
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 将任务队列排到一个新列表中，通常使用drainTo。
     * 但是，如果队列是一个延迟队列或任何其他类型的队列，
     * poll或drainTo可能无法删除某些元素，那么它将逐个删除这些元素。
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /* Extension hooks */

    /**
     * 空方法
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * 空方法
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * 空方法
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {

        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
