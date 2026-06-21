package com.nook.biz.node.api.xray.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享渲染请求: 一个客户在一台线路机上的渲染入参; 协议特定渲染收口 node 协议实现
 *
 * @author nook
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareRenderReqDTO {

    /** 调用方回填键 (如凭证 ID); 渲染结果按此键回, 拼不出连接的 reqKey 不在结果里. */
    private String reqKey;

    /** 线路机 ID. */
    private String serverId;

    /** 客户连接身份 (uuid / 密钥). */
    private String uuid;

    /** 节点展示名: vmess ps / vless #备注 / clash name; 调用方按格式自定 (协议内按需 urlEncode). */
    private String label;
}
