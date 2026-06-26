package com.nook.biz.node.service.xray.config;

import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;

/**
 * Xray inbound 共享配置 Service 接口
 *
 * @author nook
 */
public interface XrayInboundService {

    /**
     * 幂等写入 inbound 配置
     *
     * @param entity inbound 配置
     */
    void upsert(XrayInboundDO entity);

    /**
     * 按 serverId 取 inbound 配置
     *
     * @param serverId 服务器编号
     * @return inbound 配置
     */
    XrayInboundDO get(String serverId);

    /**
     * 获得 inbound 共享配置详情 (协议字段经 formPrefill 投影成 formValues 供前端预填); 未装机返 null
     *
     * @param serverId 服务器编号
     * @return inbound 详情 VO
     */
    XrayInboundRespVO getInboundDetail(String serverId);
}
