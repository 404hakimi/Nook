package com.nook.common.web.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** 分页查询统一返回结构: { total, records }；与具体 ORM 解耦。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private List<T> records;

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, Collections.emptyList());
    }

    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records);
    }

    /** 把 List<E> 经 mapper 转换为 List<T>，便于 entity → VO 转换。 */
    public <E> PageResult<E> map(Function<T, E> mapper) {
        return new PageResult<>(total, records.stream().map(mapper).toList());
    }
}
