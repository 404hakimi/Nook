package com.nook.biz.agent.controller.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Agent xray 流量上报 Request VO
 *
 * @author nook
 */
@Data
public class AgentXrayTrafficReqVO {

    @NotNull(message = "stats 不能为空")
    @Valid
    private List<Row> stats;

    @Data
    public static class Row {
        @NotBlank
        @Size(max = 128)
        private String email;

        @NotNull
        @PositiveOrZero
        private Long upBytes;

        @NotNull
        @PositiveOrZero
        private Long downBytes;
    }
}
