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

    /** 实体 → 列表/详情 RespVO；密码原文不下发，只下 sshAuthConfigured 布尔。 */
    @Mapping(target = "sshAuthConfigured", ignore = true)
    ResourceServerRespVO convert(ResourceServer entity);

    List<ResourceServerRespVO> convertList(List<ResourceServer> entities);

    default PageResult<ResourceServerRespVO> convertPage(PageResult<ResourceServer> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    @AfterMapping
    default void fillCredentialFlags(ResourceServer src, @MappingTarget ResourceServerRespVO target) {
        target.setSshAuthConfigured(
                StrUtil.isNotBlank(src.getSshPassword()) || StrUtil.isNotBlank(src.getSshPrivateKey()));
    }

    /** 实体 → 跨模块凭据 DTO；带原文 SSH 密码/私钥，仅在 nook 内部传递。 */
    default ServerCredentialDTO toCredential(ResourceServer e) {
        if (e == null) return null;
        return ServerCredentialDTO.builder()
                .serverId(e.getId())
                .sshHost(e.getHost())
                .sshPort(e.getSshPort() == null ? 22 : e.getSshPort())
                .sshUser(StrUtil.blankToDefault(e.getSshUser(), "root"))
                .sshPassword(e.getSshPassword())
                .sshPrivateKey(e.getSshPrivateKey())
                .sshTimeoutSeconds(e.getSshTimeoutSeconds())
                .xrayGrpcHost(e.getXrayGrpcHost())
                .xrayGrpcPort(e.getXrayGrpcPort())
                .backendTimeoutSeconds(e.getBackendTimeoutSeconds())
                .build();
    }
}
