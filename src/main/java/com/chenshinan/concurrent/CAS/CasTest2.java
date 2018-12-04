package com.chenshinan.concurrent.CAS;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author shinan.chen
 * @since 2018/12/4
 */
public class CasTest2 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        AtomicReference<String> mode = new AtomicReference<>("A");
        AtomicInteger frequence = new AtomicInteger(scanner.nextInt());
        Thread tA = new Thread(new ThreadA(mode, frequence));
        tA.start();
        Thread tL = new Thread(new ThreadL(mode, frequence));
        tL.start();
        Thread tI = new Thread(new ThreadI(mode, frequence));
        tI.start();
    }

    static class ThreadA implements Runnable {
        private AtomicReference<String> mode;
        private AtomicInteger frequence;

        public ThreadA(AtomicReference<String> mode, AtomicInteger frequence) {
            this.mode = mode;
            this.frequence = frequence;
        }

        @Override
        public void run() {
            while (frequence.get() > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(mode.compareAndSet("A","L")){
                    System.out.print("A");
                }
            }
        }
    }

    static class ThreadL implements Runnable {
        private AtomicReference<String> mode;
        private AtomicInteger frequence;


        public ThreadL(AtomicReference<String> mode, AtomicInteger frequence) {
            this.mode = mode;
            this.frequence = frequence;
        }

        @Override
        public void run() {
//            System.out.println(frequence.get());
            while (frequence.get() > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(mode.compareAndSet("L","I")){
                    System.out.print("L");
                }
            }
        }
    }

    static class ThreadI implements Runnable {
        private AtomicReference<String> mode;
        private AtomicInteger frequence;

        public ThreadI(AtomicReference<String> mode, AtomicInteger frequence) {
            this.mode = mode;
            this.frequence = frequence;
        }

        @Override
        public void run() {
            while (frequence.get() > 0) {
                if(mode.compareAndSet("I","A")){
                    System.out.print("I");
                    frequence.decrementAndGet();
                }
            }

        }
    }
}
