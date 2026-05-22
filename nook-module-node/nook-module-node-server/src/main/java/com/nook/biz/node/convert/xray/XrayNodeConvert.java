package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XrayNodeRespVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray 节点 Convert.
 *
 * @author nook
 */
@Mapper
public interface XrayNodeConvert {

    XrayNodeConvert INSTANCE = Mappers.getMapper(XrayNodeConvert.class);

    XrayNodeRespVO convert(XrayNodeDO entity);

    List<XrayNodeRespVO> convertList(List<XrayNodeDO> entities);

    /** systemd unit 路径是全节点固定常量, 后端回填, 前端零拼接. */
    @AfterMapping
    default void fillFixedPaths(@MappingTarget XrayNodeRespVO vo) {
        vo.setXraySystemdUnitPath(XrayConstants.SYSTEMD_UNIT_PATH);
    }

    default PageResult<XrayNodeRespVO> convertPage(PageResult<XrayNodeDO> page,
                                                   Map<String, ResourceServerDO> serverMap) {
        List<XrayNodeRespVO> records = convertList(page.getRecords());
        for (XrayNodeRespVO v : records) {
            fillServer(v, serverMap);
        }
        return PageResult.of(page.getTotal(), records);
    }

    /** 抽 serverId 去重集合, 供 controller 一次性查 server map. */
    static Set<String> collectServerIds(Collection<XrayNodeDO> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptySet();
        return entities.stream()
                .map(XrayNodeDO::getServerId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    private static void fillServer(XrayNodeRespVO vo, Map<String, ResourceServerDO> serverMap) {
        if (vo == null || serverMap == null) return;
        ResourceServerDO s = serverMap.get(vo.getServerId());
        if (s != null) {
            vo.setServerName(s.getName());
            vo.setServerHost(s.getHost());
        }
    }
}
