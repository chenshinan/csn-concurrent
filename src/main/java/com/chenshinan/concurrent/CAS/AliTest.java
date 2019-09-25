package com.chenshinan.concurrent.CAS;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 阿里巴巴面试题
 * 多线程：3个线程，线程A.B.C，线程A只打印”A“，线程B只打印”L“，线程C只打印”I“，要求3个线程以顺序”ALI“打印，
 * ”ALI“的个数由线程A控制，例如输入3，打印”ALIALIALI“，限时20minute
 *
 * @author dinghuang123@gmail.com
 * @since 2018/11/30
 */
public class AliTest {

    private static Integer valueA = 1;
    private static Integer frequence = 3;
    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) {
        doSomething();

    }

    private static void doSomething() {
        for (int i = 0; i < 10; i++) {
            Thread threadA = new ThreadA();
            Thread threadB = new ThreadB();
            Thread threadC = new ThreadC();
            threadA.start();
            threadB.start();
            threadC.start();
        }
    }

    static class ThreadA extends Thread {

        @Override
        public void run() {
            while (frequence > 0) {
                lock.lock();
                if (lock.isLocked() && valueA == 1) {
                    System.out.print("A");
                    valueA = 2;
                }
                lock.unlock();
            }
        }
    }

    static class ThreadB extends Thread {

        @Override
        public void run() {
            while (frequence > 0) {
                lock.lock();
                if (lock.isLocked() && valueA == 2) {
                    System.out.print("L");
                    valueA = 3;
                }
                lock.unlock();
            }
        }
    }

    static class ThreadC extends Thread {

        @Override
        public void run() {
            while (frequence > 0) {
                lock.lock();
                if (lock.isLocked() && valueA == 3) {
                    System.out.print("I");
                    frequence = frequence - 1;
                    valueA = 1;
                }
                lock.unlock();
            }
        }
    }

}
