package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayInboundDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 接入配置 Api 接口
 *
 * @author nook
 */
public interface XrayConfigApi {

    /**
     * 批量取线路机的 xray 接入连接参数
     *
     * <p>未装 xray / 拼不出 host 的线路机不入返回 map.
     *
     * @param serverIds 线路机ID集合
     * @return 线路机ID → 接入连接参数
     */
    Map<String, XrayInboundDTO> listInboundByServerIds(Collection<String> serverIds);
}
