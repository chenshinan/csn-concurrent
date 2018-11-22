package com.chenshinan.concurrent.CountDownLacth;

import com.chenshinan.concurrent.CountDownLacth.MyCountDownLatch;

/**
 * 能够使一个线程在等待另外一些线程完成各自工作之后，再继续执行
 *
 * @author shinan.chen
 * @since 2018/11/21
 */
public class CountDownLacthTest {
    public static void main(String[] args){
        final MyCountDownLatch latch = new MyCountDownLatch(10);
        for(int i=0; i< 10; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("线程" + Thread.currentThread().getId() + "准备减少同步状态");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("线程" + Thread.currentThread().getId() + "成功减少同步状态");
                    latch.countDown();
                }
            }).start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("10个线程已经执行完毕！开始计算排名");
    }
}
