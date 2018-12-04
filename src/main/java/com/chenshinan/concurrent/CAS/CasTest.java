package com.chenshinan.concurrent.CAS;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author shinan.chen
 * @since 2018/12/4
 */
public class CasTest {
    public static volatile String modeStr = "A";
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
                if (modeStr.equals("A")) {
                    System.out.print("A");
                    modeStr = "L";
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
                if (modeStr.equals("L")) {
                    System.out.print("L");
                    modeStr = "I";
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
                if (modeStr.equals("I")) {
                    System.out.print("I");
                    frequence.decrementAndGet();
                    modeStr = "A";
                }
            }

        }
    }
}
