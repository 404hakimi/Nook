package com.nook.biz.resource.convert;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.controller.server.vo.ResourceServerRespVO;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ResourceServer 实体 ↔ VO/DTO 转换。 */
@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    /** 实体 → 列表/详情 RespVO；密码原文不下发，只下"是否已配置"的布尔标志(在 @AfterMapping 里写入)。 */
    @Mapping(target = "sshAuthConfigured", ignore = true)
    @Mapping(target = "panelPasswordConfigured", ignore = true)
    ResourceServerRespVO convert(ResourceServer entity);

    List<ResourceServerRespVO> convertList(List<ResourceServer> entities);

    default PageResult<ResourceServerRespVO> convertPage(PageResult<ResourceServer> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    @AfterMapping
    default void fillCredentialFlags(ResourceServer src, @MappingTarget ResourceServerRespVO target) {
        target.setSshAuthConfigured(StrUtil.isNotBlank(src.getSshPassword()) || StrUtil.isNotBlank(src.getSshPrivateKey()));
        target.setPanelPasswordConfigured(StrUtil.isNotBlank(src.getPanelPassword()));
    }

    /**
     * 实体 → 跨模块凭据 DTO；带原文密码——仅在模块边界内部传递。
     * 此方法是 default 而非 MapStruct 生成，以避开 record 的 builder 在 MapStruct 1.6 下的兼容问题。
     */
    default ServerCredentialDTO toCredential(ResourceServer e) {
        if (e == null) return null;
        return ServerCredentialDTO.builder()
                .serverId(e.getId())
                .backendType(e.getBackendType())
                .sshHost(e.getHost())
                .sshPort(e.getSshPort() == null ? 22 : e.getSshPort())
                .sshUser(StrUtil.blankToDefault(e.getSshUser(), "root"))
                .sshPassword(e.getSshPassword())
                .sshPrivateKey(e.getSshPrivateKey())
                .sshTimeoutSeconds(e.getSshTimeoutSeconds())
                .panelBaseUrl(e.getPanelBaseUrl())
                .panelUsername(e.getPanelUsername())
                .panelPassword(e.getPanelPassword())
                .panelIgnoreTls(e.getPanelIgnoreTls() != null && e.getPanelIgnoreTls() == 1)
                .xrayGrpcHost(e.getXrayGrpcHost())
                .xrayGrpcPort(e.getXrayGrpcPort())
                .timeoutSeconds(0) // 走 DTO.timeoutSecondsOrDefault 的 8s 兜底
                .build();
    }
}
