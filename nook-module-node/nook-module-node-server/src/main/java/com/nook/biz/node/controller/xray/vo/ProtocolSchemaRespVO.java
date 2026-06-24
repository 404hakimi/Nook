package com.nook.biz.node.controller.xray.vo;

import com.nook.biz.node.framework.xray.inbound.InboundFieldSchema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 单个入站协议的装机表单 schema (协议下拉 + 该协议字段); 前端动态渲染装机表单用
 *
 * @author nook
 */
@Data
public class ProtocolSchemaRespVO {

    /** 协议判别键 (vmess / vless); = 提交 inbound.protocol. */
    private String protocol;

    /** 协议显示名 (下拉 label). */
    private String label;

    /** 该协议的装机表单字段. */
    private List<InboundFieldSchema> fields;
}
