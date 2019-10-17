package com.chenshinan.concurrent.standardTest;

import java.util.Arrays;
import java.util.List;

/**
 * @author shinan.chen
 * @since 2019/9/26
 */
public class StandardTest {
    public static void main1(String[] args) {
        List<String> list = Arrays.asList("xx", "bb");
        list.add("cc");
        System.out.println(list);
    }

    public static void main(String[] args) {
        method(null);
    }

    /**
     * 爱上
     * @param param
     */
    public static void method(String param) {
        switch (param) {
            case "sth":
                System.out.println("it's sth");
                break;
            case "null":
                System.out.println("it's null");
                break;
            default:
                System.out.println("default");
        }
    }
}
