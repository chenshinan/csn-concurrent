package com.chenshinan.concurrent.AQS;

/**
 * 能够使多个线程在等待另外一些线程完成各自工作之后，再继续执行
 *
 * @author shinan.chen
 * @since 2018/11/21
 */
public class CountDownLacthTest2 {
    public static void main(String[] args){
        final MyCountDownLatch latch = new MyCountDownLatch(5);

        for(int i=0; i< 3; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {

                    System.out.println("等待线程" + Thread.currentThread().getId() + "开始执行");
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("等待线程" + Thread.currentThread().getId() + "已被释放");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("等待线程" + Thread.currentThread().getId() + "完成执行");
                }
            }).start();
        }

        for(int i=0; i< 5; i++){

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
    }
}
