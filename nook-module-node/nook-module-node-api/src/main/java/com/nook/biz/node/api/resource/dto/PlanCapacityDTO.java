package com.nook.biz.node.api.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 套餐落地机池容量 DTO (按套餐规格匹配后按 status 分桶)
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanCapacityDTO {

    /** 匹配的落地机总数. */
    private int total;

    /** 其中可分配数. */
    private int available;

    /** 其中已占用数. */
    private int occupied;
}
