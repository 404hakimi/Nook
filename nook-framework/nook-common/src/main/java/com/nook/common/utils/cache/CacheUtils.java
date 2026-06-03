package com.nook.common.utils.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 本地缓存 (Guava LoadingCache) 构建工具.
 *
 * @author nook
 */
public class CacheUtils {

    private CacheUtils() {}

    /** LoadingCache 最大条目数. */
    private static final int CACHE_MAX_SIZE = 10000;

    /**
     * 构建异步刷新的 LoadingCache: 到刷新点只阻塞当前加载线程、其余线程拿旧值, 且加载动作本身也异步执行.
     *
     * <p>适用于"全局 / 系统"维度、与 ThreadLocal 无关的缓存; 若缓存内容依赖 ThreadLocal,
     * 改用 {@link #buildCache(Duration, CacheLoader)} 或自行传递 ThreadLocal.
     *
     * @param duration 刷新间隔 (refreshAfterWrite)
     * @param loader   加载器
     * @return LoadingCache
     */
    public static <K, V> LoadingCache<K, V> buildAsyncReloadingCache(Duration duration, CacheLoader<K, V> loader) {
        return CacheBuilder.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .refreshAfterWrite(duration)
                .build(CacheLoader.asyncReloading(loader, Executors.newCachedThreadPool()));
    }

    /**
     * 构建同步刷新的 LoadingCache: 到刷新点只阻塞当前加载线程、其余线程拿旧值.
     *
     * @param duration 刷新间隔 (refreshAfterWrite)
     * @param loader   加载器
     * @return LoadingCache
     */
    public static <K, V> LoadingCache<K, V> buildCache(Duration duration, CacheLoader<K, V> loader) {
        return CacheBuilder.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .refreshAfterWrite(duration)
                .build(loader);
    }

}
