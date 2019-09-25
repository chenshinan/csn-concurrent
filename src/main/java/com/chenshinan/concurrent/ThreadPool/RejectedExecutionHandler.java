
package com.chenshinan.concurrent.ThreadPool;

public interface RejectedExecutionHandler {

    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
