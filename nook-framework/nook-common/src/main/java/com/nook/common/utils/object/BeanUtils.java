package com.nook.common.utils.object;

import cn.hutool.core.bean.BeanUtil;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bean 工具类.
 * 默认用 {@link cn.hutool.core.bean.BeanUtil} 作为底层实现; 复杂对象转换走 mapstruct + default 配合.
 *
 * @author nook
 */
public class BeanUtils {

    /**
     * 把 source bean 的所有同名字段拷贝到一个新 targetClass 实例.
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         目标类型
     * @return 已填充的新 targetClass 实例; source 为 null 时返回 null
     */
    public static <T> T toBean(Object source, Class<T> targetClass) {
        return BeanUtil.toBean(source, targetClass);
    }

    /**
     * toBean + 后处理回调 (例如做 mask 或补字段).
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param peek        新对象的后处理回调
     * @param <T>         目标类型
     * @return 已填充并 peek 处理后的新实例
     */
    public static <T> T toBean(Object source, Class<T> targetClass, Consumer<T> peek) {
        T target = toBean(source, targetClass);
        if (target != null) {
            peek.accept(target);
        }
        return target;
    }

    /**
     * 列表批量 toBean.
     *
     * @param source     源列表
     * @param targetType 目标类型
     * @param <S>        源元素类型
     * @param <T>        目标元素类型
     * @return 转换后的列表; source 为 null 时返回 null
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return CollectionUtils.convertList(source, s -> toBean(s, targetType));
    }

    /**
     * 列表批量 toBean + 每项 peek.
     *
     * @param source     源列表
     * @param targetType 目标类型
     * @param peek       每项的后处理回调
     * @param <S>        源元素类型
     * @param <T>        目标元素类型
     * @return 转换后的列表
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType, Consumer<T> peek) {
        List<T> list = toBean(source, targetType);
        if (list != null) {
            list.forEach(peek);
        }
        return list;
    }

    /**
     * 分页结果转换 (内部 records 元素 toBean).
     *
     * @param source     源分页
     * @param targetType 目标元素类型
     * @param <S>        源元素类型
     * @param <T>        目标元素类型
     * @return 转换后的分页
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType) {
        return toBean(source, targetType, null);
    }

    /**
     * 分页结果转换 + 每项 peek.
     *
     * @param source     源分页
     * @param targetType 目标元素类型
     * @param peek       每项的后处理回调
     * @param <S>        源元素类型
     * @param <T>        目标元素类型
     * @return 转换后的分页
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType, Consumer<T> peek) {
        if (source == null) {
            return null;
        }
        List<T> list = toBean(source.getRecords(), targetType);
        if (peek != null && list != null) {
            list.forEach(peek);
        }
        return PageResult.of(source.getTotal(), list);
    }

    /**
     * 同名字段拷贝, 不忽略 null (source 字段为 null 也覆盖到 target).
     *
     * @param source 源对象
     * @param target 目标对象 (已存在的实例)
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        BeanUtil.copyProperties(source, target, false);
    }
}
