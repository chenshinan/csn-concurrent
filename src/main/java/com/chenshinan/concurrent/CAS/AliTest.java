package com.chenshinan.concurrent.CAS;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
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

    public static void main(String[] args) {
        System.out.print("请输入打印次数:");
        Scanner scanner = new Scanner(System.in);
        AtomicInteger valueA = new AtomicInteger(1);
        AtomicInteger frequence = new AtomicInteger(scanner.nextInt());
        Thread threadA = new ThreadA(frequence, valueA);
        Thread threadB = new ThreadB(frequence, valueA);
        Thread threadC = new ThreadC(frequence, valueA);
        threadA.start();
        threadB.start();
        threadC.start();
    }

    static class ThreadA extends Thread {

        private Lock lock = new ReentrantLock();
        private AtomicInteger frequence;
        private AtomicInteger valueA;

        public ThreadA(AtomicInteger frequence, AtomicInteger valueA) {
            this.frequence = frequence;
            this.valueA = valueA;
        }

        @Override
        public void run() {
//            lock.lock();
//            try {
                while (frequence.get() > 0) {
                    if (valueA.get() == 1) {
                        System.out.print("A");
                        valueA.compareAndSet(valueA.get(), 2);
                    }
                }
//            } finally {
//                lock.unlock();// 释放锁
//            }
        }
    }

    static class ThreadB extends Thread {

        private Lock lock = new ReentrantLock();
        private AtomicInteger frequence;
        private AtomicInteger valueA;

        public ThreadB(AtomicInteger frequence, AtomicInteger valueA) {
            this.frequence = frequence;
            this.valueA = valueA;
        }

        @Override
        public void run() {
//            lock.lock();
//            try {
                while (frequence.get() > 0) {
                    if (valueA.get() == 2) {
                        System.out.print("L");
                        valueA.compareAndSet(valueA.get(), 3);
                    }
                }
//            } finally {
//                lock.unlock();
//            }
        }
    }

    static class ThreadC extends Thread {

        private Lock lock = new ReentrantLock();
        private AtomicInteger frequence;
        private AtomicInteger valueA;

        public ThreadC(AtomicInteger frequence, AtomicInteger valueA) {
            this.frequence = frequence;
            this.valueA = valueA;
        }

        @Override
        public void run() {
//            lock.lock();
//            try {
                while (frequence.get() > 0) {
                    if (valueA.get() == 3) {
                        System.out.print("I");
                        frequence.compareAndSet(frequence.get(), frequence.get() - 1);
                        valueA.compareAndSet(valueA.get(), 1);
                    }
                }
//            } finally {
//                lock.unlock();
//            }
        }
    }

}