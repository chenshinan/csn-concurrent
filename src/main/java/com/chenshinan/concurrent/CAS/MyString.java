package com.chenshinan.concurrent.CAS;

/**
 * @author shinan.chen
 * @since 2018/11/15
 */
public class MyString {

    private volatile int count;
    private volatile String value;

    public MyString(int count, String value) {
        this.count = count;
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
