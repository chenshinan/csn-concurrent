package com.chenshinan.concurrent.CAS;

import java.util.Scanner;

/**
 * 控制多线程按照顺序输出，并不需要用到Atomic相关类，主要考察的是volatile
 *
 * @author shinan.chen
 * @since 2018/12/4
 */
public class CasTest3 {
    public static String modeStr = "A";
    public static int count;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        MyString myString = new MyString(scanner.nextInt(),"A");
        Thread tA = new Thread(new ThreadA(myString));
        tA.start();
        Thread tA2 = new Thread(new ThreadA(myString));
        tA2.start();
        Thread tA3 = new Thread(new ThreadA(myString));
        tA3.start();
        Thread tL = new Thread(new ThreadL(myString));
        tL.start();
        Thread tI = new Thread(new ThreadI(myString));
        tI.start();
    }

    static class ThreadA implements Runnable {
        private MyString myString;

        public ThreadA(MyString myString) {
            this.myString = myString;
        }

        @Override
        public void run() {
            while (myString.getCount() > 0) {
                if (myString.getValue().equals("A")) {
                    System.out.print("A");
                    myString.setValue("L");
                }
            }
        }
    }

    static class ThreadL implements Runnable {
        private MyString myString;

        public ThreadL(MyString myString) {
            this.myString = myString;
        }

        @Override
        public void run() {
            while (myString.getCount() > 0) {
                if (myString.getValue().equals("L")) {
                    System.out.print("L");
                    myString.setValue("I");
                }
            }
        }
    }

    static class ThreadI implements Runnable {
        private MyString myString;

        public ThreadI(MyString myString) {
            this.myString = myString;
        }
        @Override
        public void run() {
            while (myString.getCount() > 0) {
                if (myString.getValue().equals("I")) {
                    System.out.print("I");
                    myString.setCount(myString.getCount()-1);
                    myString.setValue("A");
                }
            }

        }
    }
}
