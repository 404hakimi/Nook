package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;

import java.util.Collection;
import java.util.List;

/**
 * Xray 客户端节点连接信息查询契约.
 *
 * @author nook
 */
public interface XrayClientApi {

    /**
     * 批量查客户端节点连接信息
     *
     * <p>仅返回运行中、且能拼出地址的客户端; 不存在 / 未运行 / 缺配置的客户端跳过, 不抛异常.
     *
     * @param clientIds 客户端ID集合
     * @return 节点连接信息列表 (顺序不保证)
     */
    List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds);
}
