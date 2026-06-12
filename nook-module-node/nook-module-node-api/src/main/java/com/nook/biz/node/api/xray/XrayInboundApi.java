package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayInboundDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 接入配置 Api 接口
 *
 * @author nook
 */
public interface XrayInboundApi {

    /**
     * 批量取线路机的 xray 接入连接参数 (未装 xray / 无 host 的不在 map 内)
     *
     * @param serverIds 线路机ID集合
     * @return 线路机ID → 接入连接参数
     */
    Map<String, XrayInboundDTO> listInboundByServerIds(Collection<String> serverIds);
}
