package com.nook.common.web.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 分页查询请求参数基类；所有 PageReqVO 继承此类。 */
@Data
public class PageParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int PAGE_NO = 1;
    public static final int PAGE_SIZE = 10;
    /** 单页最大条数兜底；用 int 而非 Integer，否则 @Max(value = ...) 不算编译期常量。 */
    public static final int PAGE_SIZE_MAX = 100;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo = PAGE_NO;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = PAGE_SIZE_MAX, message = "每页条数不能超过 " + PAGE_SIZE_MAX)
    private Integer pageSize = PAGE_SIZE;

    /** 业务侧便利方法：获取偏移量（pageNo * pageSize 风格的 1-based 转换）。 */
    @JsonIgnore
    public long getOffset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
