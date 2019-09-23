package com.chenshinan.concurrent.CAS;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author shinan.chen
 * @since 2019/8/28
 */
public class ReentrantLock0828 {
    static final ReentrantLock lock = new ReentrantLock();
    static String value = "A";
    static int frequence = 3;

    public static void main(String[] args) {
        new ThreadA().start();
        new ThreadL().start();
        new ThreadI().start();
    }

    static class ThreadA extends Thread {
        @Override
        public void run() {
//            while (frequence > 0) {
//                lock.lock();
//                if (frequence > 0 && value.equals("A")) {
//                    System.out.print(value);
//                    value = "L";
//                }
//                lock.unlock();
                lock.lock();
                for (int i = 0; i < 10; i++) {
                    System.out.println("A");
                }
                lock.unlock();
//            }
        }
    }

    static class ThreadL extends Thread {
        @Override
        public void run() {
//            while (frequence > 0) {
//                lock.lock();
//                if (frequence > 0 && value.equals("L")) {
//                    System.out.print(value);
//                    value = "I";
//                }
//                lock.unlock();
                lock.lock();
                for (int i = 0; i < 10; i++) {
                    System.out.println("B");
                }
                lock.unlock();
//            }
        }
    }

    static class ThreadI extends Thread {
        @Override
        public void run() {
            while (frequence > 0) {
//                lock.lock();
//                if (frequence > 0 && value.equals("I")) {
//                    System.out.print(value);
//                    value = "A";
//                    frequence--;
//                }
//                lock.unlock();
                lock.lock();
                for (int i = 0; i < 10; i++) {
                    System.out.println("C");
                }
                lock.unlock();
            }
        }
    }
}
