package com.kirugoldzzzz.loadout.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LruCache<K, V> {

    private final int capacity;
    private final Map<K, V> entries;

    public LruCache(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new LinkedHashMap<>(Math.min(this.capacity, 256), 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.capacity;
            }
        };
    }

    public synchronized V get(K key) {
        return entries.get(key);
    }

    public synchronized boolean contains(K key) {
        return entries.containsKey(key);
    }

    public synchronized void put(K key, V value) {
        entries.put(key, value);
    }

    public synchronized void remove(K key) {
        entries.remove(key);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public int capacity() {
        return capacity;
    }
}
