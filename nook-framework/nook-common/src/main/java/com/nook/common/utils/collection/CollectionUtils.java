package com.nook.common.utils.collection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.ImmutableMap;
import com.nook.common.web.response.PageResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.hutool.core.convert.Convert.toCollection;
import static java.util.Arrays.asList;

/**
 * Collection 工具类.
 *
 * @author nook
 */
public class CollectionUtils {

    /**
     * source 是否在 targets 数组中 (equals 判等).
     *
     * @param source  待匹配值
     * @param targets 候选数组
     * @return true 表示 source 等于其中之一
     */
    public static boolean containsAny(Object source, Object... targets) {
        return asList(targets).contains(source);
    }

    /**
     * 任一集合为空 (null 或 size=0) 即返回 true.
     *
     * @param collections 待检查集合数组
     * @return true 表示存在空集合
     */
    public static boolean isAnyEmpty(Collection<?>... collections) {
        return Arrays.stream(collections).anyMatch(CollectionUtil::isEmpty);
    }

    /**
     * 集合存在任一元素满足 predicate.
     *
     * @param from      源集合
     * @param predicate 断言
     * @param <T>       元素类型
     * @return true 表示存在匹配
     */
    public static <T> boolean anyMatch(Collection<T> from, Predicate<T> predicate) {
        return from.stream().anyMatch(predicate);
    }

    /**
     * 过滤集合返回新 list; 空集合返回空 list.
     *
     * @param from      源集合
     * @param predicate 过滤断言
     * @param <T>       元素类型
     * @return 过滤后的新 list
     */
    public static <T> List<T> filterList(Collection<T> from, Predicate<T> predicate) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 按 keyMapper 去重, 重复时保留先出现的.
     *
     * @param from      源集合
     * @param keyMapper 唯一键提取器
     * @param <T>       元素类型
     * @param <R>       键类型
     * @return 去重后的 list
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return distinct(from, keyMapper, (t1, t2) -> t1);
    }

    /**
     * 按 keyMapper 去重, 重复时按 cover 选保留哪一个.
     *
     * @param from      源集合
     * @param keyMapper 唯一键提取器
     * @param cover     冲突时合并策略
     * @param <T>       元素类型
     * @param <R>       键类型
     * @return 去重后的 list
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper, BinaryOperator<T> cover) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(convertMap(from, keyMapper, Function.identity(), cover).values());
    }

    /**
     * 数组 → list 转换.
     *
     * @param from 源数组
     * @param func 转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return 转换后的 list
     */
    public static <T, U> List<U> convertList(T[] from, Function<T, U> func) {
        if (ArrayUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return convertList(Arrays.asList(from), func);
    }

    /**
     * 集合 → list 转换 (过滤掉转换为 null 的项).
     *
     * @param from 源集合
     * @param func 转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return 转换后的 list
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 过滤 + 转换 → list.
     *
     * @param from   源集合
     * @param func   转换函数
     * @param filter 前置过滤断言
     * @param <T>    源元素类型
     * @param <U>    目标元素类型
     * @return 转换后的 list
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(filter).map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 字符串按 delimiter 切分并 converter 转换为 list; 转换异常的项静默丢弃.
     *
     * @param text      源文本
     * @param delimiter 分隔符
     * @param converter 单项转换器
     * @param <T>       目标元素类型
     * @return 转换后的 list
     */
    public static <T> List<T> splitToList(String text, String delimiter, Function<String, T> converter) {
        if (StrUtil.isBlank(text)) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>();
        for (String item : text.split(delimiter)) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            try {
                T converted = converter.apply(trimmed);
                if (converted != null) {
                    result.add(converted);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 分页结果按 func 转换内部 records.
     *
     * @param from 源分页
     * @param func 元素转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return 转换后的分页
     */
    public static <T, U> PageResult<U> convertPage(PageResult<T> from, Function<T, U> func) {
        if (from == null) {
            return PageResult.empty();
        }
        if (CollUtil.isEmpty(from.getRecords())) {
            return PageResult.of(from.getTotal(), new ArrayList<>());
        }
        return PageResult.of(from.getTotal(), convertList(from.getRecords(), func));
    }

    /**
     * 集合 → list 的 flatMap 转换 (一项展平成多项).
     *
     * @param from 源集合
     * @param func 一对多转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return 展平后的 list
     */
    public static <T, U> List<U> convertListByFlatMap(Collection<T> from,
                                                      Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 先 map 再 flatMap 的组合.
     *
     * @param from   源集合
     * @param mapper 元素先映射
     * @param func   再展平为 stream
     * @param <T>    源元素类型
     * @param <U>    中间类型
     * @param <R>    目标类型
     * @return 展平后的 list
     */
    public static <T, U, R> List<R> convertListByFlatMap(Collection<T> from,
                                                         Function<? super T, ? extends U> mapper,
                                                         Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 把 Map of List 的所有 value 列表合并成一个 list.
     *
     * @param map 源 map
     * @param <K> key 类型
     * @param <V> value list 元素类型
     * @return 合并后的 list
     */
    public static <K, V> List<V> mergeValuesFromMap(Map<K, List<V>> map) {
        return map.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 集合转 set (元素本身).
     *
     * @param from 源集合
     * @param <T>  元素类型
     * @return set
     */
    public static <T> Set<T> convertSet(Collection<T> from) {
        return convertSet(from, v -> v);
    }

    /**
     * 集合转 set (转换后).
     *
     * @param from 源集合
     * @param func 转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return set
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 过滤 + 转换 → set.
     *
     * @param from   源集合
     * @param func   转换函数
     * @param filter 前置过滤
     * @param <T>    源元素类型
     * @param <U>    目标元素类型
     * @return set
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().filter(filter).map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 过滤 + 按 keyFunc 取值建 map (元素本身作为 value).
     *
     * @param from    源集合
     * @param filter  前置过滤
     * @param keyFunc 键提取器
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return map
     */
    public static <T, K> Map<K, T> convertMapByFilter(Collection<T> from, Predicate<T> filter, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().filter(filter).collect(Collectors.toMap(keyFunc, v -> v));
    }

    /**
     * 集合 → set 的 flatMap (一项展平成多项).
     *
     * @param from 源集合
     * @param func 一对多转换函数
     * @param <T>  源元素类型
     * @param <U>  目标元素类型
     * @return set
     */
    public static <T, U> Set<U> convertSetByFlatMap(Collection<T> from,
                                                    Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 先 map 再 flatMap 转 set.
     *
     * @param from   源集合
     * @param mapper 元素先映射
     * @param func   再展平为 stream
     * @param <T>    源元素类型
     * @param <U>    中间类型
     * @param <R>    目标类型
     * @return set
     */
    public static <T, U, R> Set<R> convertSetByFlatMap(Collection<T> from,
                                                       Function<? super T, ? extends U> mapper,
                                                       Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 集合按 keyFunc 取键建 map (元素本身作为 value).
     *
     * @param from    源集合
     * @param keyFunc 键提取器
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return map
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return convertMap(from, keyFunc, Function.identity());
    }

    /**
     * convertMap with custom map supplier.
     *
     * @param from     源集合
     * @param keyFunc  键提取器
     * @param supplier 目标 map 工厂
     * @param <T>      元素类型
     * @param <K>      键类型
     * @return map
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc, Supplier<? extends Map<K, T>> supplier) {
        if (CollUtil.isEmpty(from)) {
            return supplier.get();
        }
        return convertMap(from, keyFunc, Function.identity(), supplier);
    }

    /**
     * 按 keyFunc / valueFunc 拆 map.
     *
     * @param from      源集合
     * @param keyFunc   键提取器
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1);
    }

    /**
     * convertMap with merge function.
     *
     * @param from          源集合
     * @param keyFunc       键提取器
     * @param valueFunc     值提取器
     * @param mergeFunction 冲突合并策略
     * @param <T>           元素类型
     * @param <K>           键类型
     * @param <V>           值类型
     * @return map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, BinaryOperator<V> mergeFunction) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return convertMap(from, keyFunc, valueFunc, mergeFunction, HashMap::new);
    }

    /**
     * convertMap with custom map supplier.
     *
     * @param from      源集合
     * @param keyFunc   键提取器
     * @param valueFunc 值提取器
     * @param supplier  目标 map 工厂
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, Supplier<? extends Map<K, V>> supplier) {
        if (CollUtil.isEmpty(from)) {
            return supplier.get();
        }
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1, supplier);
    }

    /**
     * convertMap 完整重载: 支持 merge + supplier.
     *
     * @param from          源集合
     * @param keyFunc       键提取器
     * @param valueFunc     值提取器
     * @param mergeFunction 冲突合并策略
     * @param supplier      目标 map 工厂
     * @param <T>           元素类型
     * @param <K>           键类型
     * @param <V>           值类型
     * @return map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, BinaryOperator<V> mergeFunction, Supplier<? extends Map<K, V>> supplier) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction, supplier));
    }

    /**
     * 按 keyFunc 分组建 multi-map (key → List of T).
     *
     * @param from    源集合
     * @param keyFunc 键提取器
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return multi-map
     */
    public static <T, K> Map<K, List<T>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(t -> t, Collectors.toList())));
    }

    /**
     * 按 keyFunc 分组 + valueFunc 转值 (key → List of V).
     *
     * @param from      源集合
     * @param keyFunc   键提取器
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return multi-map
     */
    public static <T, K, V> Map<K, List<V>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream()
                .collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toList())));
    }

    /**
     * 按 keyFunc 分组 + valueFunc 转值, value 用 Set 去重 (key → Set of V).
     *
     * @param from      源集合
     * @param keyFunc   键提取器
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return key → Set of V
     */
    public static <T, K, V> Map<K, Set<V>> convertMultiMap2(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toSet())));
    }

    /**
     * 按 keyFunc 建不可变 map.
     *
     * @param from    源集合
     * @param keyFunc 键提取器
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return Guava ImmutableMap
     */
    public static <T, K> Map<K, T> convertImmutableMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, T> builder = ImmutableMap.builder();
        from.forEach(item -> builder.put(keyFunc.apply(item), item));
        return builder.build();
    }

    /**
     * 对比老 / 新两个列表, 按 sameFunc 判定相同, 拆出 [新增, 修改, 删除] 三组.
     *
     * @param oldList  老列表
     * @param newList  新列表
     * @param sameFunc 同元素判定 (true 表示是同一条)
     * @param <T>      元素类型
     * @return [createList, updateList, deleteList]
     */
    public static <T> List<List<T>> diffList(Collection<T> oldList, Collection<T> newList,
                                             BiFunction<T, T, Boolean> sameFunc) {
        List<T> createList = new LinkedList<>(newList);
        List<T> updateList = new ArrayList<>();
        List<T> deleteList = new ArrayList<>();

        for (T oldObj : oldList) {
            T foundObj = null;
            for (Iterator<T> iterator = createList.iterator(); iterator.hasNext(); ) {
                T newObj = iterator.next();
                if (!sameFunc.apply(oldObj, newObj)) {
                    continue;
                }
                iterator.remove();
                foundObj = newObj;
                break;
            }
            if (foundObj != null) {
                updateList.add(foundObj);
            } else {
                deleteList.add(oldObj);
            }
        }
        return asList(createList, updateList, deleteList);
    }

    /**
     * source 与 candidates 是否有任一交集元素 (走 hutool 实现, 不依赖 spring-core).
     *
     * @param source     源集合
     * @param candidates 候选集合
     * @return true 表示有交集
     */
    public static boolean containsAny(Collection<?> source, Collection<?> candidates) {
        if (CollUtil.isEmpty(source) || CollUtil.isEmpty(candidates)) {
            return false;
        }
        return candidates.stream().anyMatch(source::contains);
    }

    /**
     * 取 list 第一项, 空 list 返 null.
     *
     * @param from 源 list
     * @param <T>  元素类型
     * @return 第一项或 null
     */
    public static <T> T getFirst(List<T> from) {
        return !CollectionUtil.isEmpty(from) ? from.get(0) : null;
    }

    /**
     * 找第一个匹配项, 没匹配返 null.
     *
     * @param from      源集合
     * @param predicate 匹配断言
     * @param <T>       元素类型
     * @return 匹配项或 null
     */
    public static <T> T findFirst(Collection<T> from, Predicate<T> predicate) {
        return findFirst(from, predicate, Function.identity());
    }

    /**
     * 找第一个匹配项并 func 转换.
     *
     * @param from      源集合
     * @param predicate 匹配断言
     * @param func      转换函数
     * @param <T>       源元素类型
     * @param <U>       目标元素类型
     * @return 转换后的匹配项或 null
     */
    public static <T, U> U findFirst(Collection<T> from, Predicate<T> predicate, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) {
            return null;
        }
        return from.stream().filter(predicate).findFirst().map(func).orElse(null);
    }

    /**
     * 取集合内 valueFunc 提取后的最大值.
     *
     * @param from      源集合
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <V>       可比较值类型
     * @return 最大值或 null
     */
    public static <T, V extends Comparable<? super V>> V getMaxValue(Collection<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return null;
        }
        assert !from.isEmpty();
        T t = from.stream().max(Comparator.comparing(valueFunc)).get();
        return valueFunc.apply(t);
    }

    /**
     * 取列表内 valueFunc 提取后的最小值.
     *
     * @param from      源 list
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <V>       可比较值类型
     * @return 最小值或 null
     */
    public static <T, V extends Comparable<? super V>> V getMinValue(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return null;
        }
        assert !from.isEmpty();
        T t = from.stream().min(Comparator.comparing(valueFunc)).get();
        return valueFunc.apply(t);
    }

    /**
     * 取列表内 valueFunc 比较的最小元素本身.
     *
     * @param from      源 list
     * @param valueFunc 值提取器
     * @param <T>       元素类型
     * @param <V>       可比较值类型
     * @return 最小元素或 null
     */
    public static <T, V extends Comparable<? super V>> T getMinObject(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return null;
        }
        assert !from.isEmpty();
        return from.stream().min(Comparator.comparing(valueFunc)).get();
    }

    /**
     * 集合按 valueFunc + accumulator 求和.
     *
     * @param from        源集合
     * @param valueFunc   值提取器
     * @param accumulator 求和函数
     * @param <T>         元素类型
     * @param <V>         可比较值类型
     * @return 求和结果或 null
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator) {
        return getSumValue(from, valueFunc, accumulator, null);
    }

    /**
     * 集合按 valueFunc + accumulator 求和; 空集合返默认值.
     *
     * @param from         源集合
     * @param valueFunc    值提取器
     * @param accumulator  求和函数
     * @param defaultValue 空集合时返回的默认值
     * @param <T>          元素类型
     * @param <V>          可比较值类型
     * @return 求和结果或默认值
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator, V defaultValue) {
        if (CollUtil.isEmpty(from)) {
            return defaultValue;
        }
        assert !from.isEmpty();
        return from.stream().map(valueFunc).filter(Objects::nonNull).reduce(accumulator).orElse(defaultValue);
    }

    /**
     * 把非 null 的 item 加进 collection.
     *
     * @param coll 目标集合
     * @param item 待加项
     * @param <T>  元素类型
     */
    public static <T> void addIfNotNull(Collection<T> coll, T item) {
        if (item == null) {
            return;
        }
        coll.add(item);
    }

    /**
     * obj 非 null 时返单元素集合; null 返空集合.
     *
     * @param obj 元素
     * @param <T> 元素类型
     * @return 单元素集合或空集合
     */
    public static <T> Collection<T> singleton(T obj) {
        return obj == null ? Collections.emptyList() : Collections.singleton(obj);
    }

    /**
     * 把 list 列表展平成单层 list.
     *
     * @param list 嵌套 list
     * @param <T>  元素类型
     * @return 展平后的 list
     */
    public static <T> List<T> newArrayList(List<List<T>> list) {
        return list.stream().filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * 把任意值转成 LinkedHashSet (元素按入序).
     *
     * @param elementType 集合元素类型
     * @param value       源值
     * @param <T>         元素类型
     * @return LinkedHashSet
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> toLinkedHashSet(Class<T> elementType, Object value) {
        return (LinkedHashSet<T>) toCollection(LinkedHashSet.class, elementType, value);
    }
}
