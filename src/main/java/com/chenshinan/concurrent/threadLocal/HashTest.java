package com.chenshinan.concurrent.threadLocal;

import com.google.common.hash.Hashing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HashTest {

    private static final int size = 16;
    private static final int threshold = size - 1;
    private static final AtomicInteger atomic = new AtomicInteger();
    private static final int HASH_INCREMENT = 0x61c88647;

    public static void main(String[] args) {
        List<Integer> hashCode = new ArrayList<>();
        List<Integer> fiboHash = new ArrayList<>();
        List<Integer> murmurHash = new ArrayList<>();
        List<Integer> consistentHash = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Object a = new Object();
            hashCode.add(a.hashCode() & threshold);
            fiboHash.add(atomic.getAndAdd(HASH_INCREMENT) & threshold);
            murmurHash.add(Hashing.murmur3_32().hashInt(i).asInt() & threshold);
            consistentHash.add(Hashing.consistentHash(i, size) & threshold);
        }

        System.out.println(hashCode.stream().sorted().map(x -> String.valueOf(x)).collect(Collectors.joining(",")));
        System.out.println(fiboHash.stream().sorted().map(x -> String.valueOf(x)).collect(Collectors.joining(",")));
        System.out.println(murmurHash.stream().sorted().map(x -> String.valueOf(x)).collect(Collectors.joining(",")));
        System.out.println(consistentHash.stream().sorted().map(x -> String.valueOf(x)).collect(Collectors.joining(",")));
    }
}
