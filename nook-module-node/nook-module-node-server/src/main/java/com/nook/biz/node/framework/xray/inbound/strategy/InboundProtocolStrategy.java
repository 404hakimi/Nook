package com.nook.biz.node.framework.xray.inbound.strategy;

import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;

/**
 * 入站协议装机策略; 每协议形态封装自己的校验 / 参数生成 / 模板占位符组装
 *
 * @author nook
 */
public interface InboundProtocolStrategy {

    /**
     * 是否处理此装机请求 (按协议形态判定)
     *
     * @param reqVO 装机入参
     * @return 是否匹配
     */
    boolean supports(XrayInstallReqVO reqVO);

    /**
     * 协议特定的装机参数校验
     *
     * @param serverId 服务器ID (subdomain 唯一性排除自身)
     * @param reqVO    装机入参
     */
    void validate(String serverId, XrayInstallReqVO reqVO);

    /**
     * 算出该协议的形态 / 语义参数 / 模板占位符 (含动态生成密钥等)
     *
     * @param reqVO      装机入参
     * @param fullDomain 完整 FQDN (绑域名时), 否则空
     * @return 协议产出
     */
    InboundProvision provision(XrayInstallReqVO reqVO, String fullDomain);
}
