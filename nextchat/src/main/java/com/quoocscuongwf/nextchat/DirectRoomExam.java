package com.quoocscuongwf.nextchat;
import java.util.*;
import java.util.concurrent.*;

public class DirectRoomExam {
    static final Map<String, Long> roomByKey = new HashMap<>();
    static final Map<Long, Set<Long>> members = new HashMap<>();
    static long nextRoomId = 100;
    static final ConcurrentHashMap<Long, Set<String>> roomMembers = new ConcurrentHashMap<>();
    //Todo 1: directRoomKey(a,b): khoa cap nguoi dung khong phu thuoc  thu tu: LEAST(a,b) + ":" + GREATEST(a,b)
    public static String directRoomKey(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
    //Todo 2: getOrCreateDirectRoomId(a,b): lay roomId tu directRoomKey(a,b) neu chua co thi tao moi va luu vao roomByKey
    //- Lay khoa = directRoomKey(a,b); lay monitor advisory.computeIfAbsent(roomByKey, key, () -> new Object()); synchronized tren no
    //- co phong thi tra id phong do, chu co thi tao moi id phong = nextRoomId++; luu vao roomByKey, tao moi set members cho phong do, tra id phong do
    public static long getOrCreateDirectRoomId(long a, long b) {
        String key = directRoomKey(a, b);
        synchronized (roomByKey) {
            if (roomByKey.containsKey(key)) {
                return roomByKey.get(key);
            } else {
                long roomId = nextRoomId++;
                roomByKey.put(key, roomId);
                members.put(roomId, new HashSet<>());
                return roomId;
            }
        }
    }
    static long[] runPair(Long pair[]) throws exception {
        CyclicBarrier barrier = new CyclicBarrier(pair.length);
        ExecutorService executor = Executors.newFixedThreadPool(pair.length);
        List<Future<Long>> futures = new ArrayList<>();
        for (Long userId : pair) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return getOrCreateDirectRoomId(userId[0], userId[1]);
            }));
        }
        long[] roomIds = new long[pair.length];
        for (int i = 0; i < pair.length; i++) {
            roomIds[i] = futures.get(i).get();
        }
        executor.shutdown();
        return roomIds;
    }
    public static void main(String[] args) throws Exception {
        System.out.println("khoa(1,3): " + directRoomKey(1, 3) + ", khoa(3,1): " + directRoomKey(3, 1));
        long[] roomIds = runPair(new Long[][]{{1, 2}, {2, 1}, {1, 2}, {1, 3}});
        System.out.println("(1,2) (2,1) (1,2) cung mot phong: " + (roomIds[0] == roomIds[1] && roomIds[1] == roomIds[2])+ ", (1,3) khac phong (1,2): " + (roomIds[0] != roomIds[3]));
        System.out.println("so phong: " + roomByKey.size() );
        for (var e: new TreeMap<>(roomByKey).entrySet()) {
            System.out.println("khoa: " + e.getKey() + " ->" + members.get(e.getValue()).size() + " thanh vien" + members.get(e.getValue()));
        }
    }
}