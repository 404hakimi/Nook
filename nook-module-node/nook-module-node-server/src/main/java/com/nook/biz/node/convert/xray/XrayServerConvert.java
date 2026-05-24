package com.nook.biz.node.convert.xray;

import com.nook.biz.node.controller.xray.vo.XrayServerRespVO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.framework.xray.XrayConstants;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.Map;

/**
 * Xray 实例元数据 Convert
 *
 * @author nook
 */
@Mapper
public interface XrayServerConvert {

    XrayServerConvert INSTANCE = Mappers.getMapper(XrayServerConvert.class);

    XrayServerRespVO convert(XrayServerDO entity);

    /** systemd unit 路径是全节点固定常量, 后端回填, 前端零拼接 */
    @AfterMapping
    default void fillFixedPaths(@MappingTarget XrayServerRespVO vo) {
        vo.setXraySystemdUnitPath(XrayConstants.SYSTEMD_UNIT_PATH);
    }

    /** host 来自 resource_server_credential, 跟 name 分两 map 注入 */
    static void fillServer(XrayServerRespVO vo,
                           Map<String, ResourceServerDO> serverMap,
                           Map<String, String> hostMap) {
        if (vo == null) return;
        if (serverMap != null) {
            ResourceServerDO s = serverMap.get(vo.getServerId());
            if (s != null) vo.setServerName(s.getName());
        }
        if (hostMap != null) {
            String h = hostMap.get(vo.getServerId());
            if (h != null) vo.setServerHost(h);
        }
    }
}
