package com.nook.biz.resource.convert;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.controller.server.vo.ResourceServerRespVO;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ResourceServer 实体 ↔ VO/DTO 转换。 */
@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    /** 实体 → 列表/详情 RespVO; 含 sshPassword / sshPrivateKey 明文 (DB 明文存, 与运营受信场景对齐)。 */
    ResourceServerRespVO convert(ResourceServer entity);

    List<ResourceServerRespVO> convertList(List<ResourceServer> entities);

    default PageResult<ResourceServerRespVO> convertPage(PageResult<ResourceServer> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /**
     * 实体 → 跨模块凭据 DTO; 带原文 SSH 密码 / 私钥, 仅 nook 内部传递。
     * resource_server 表的 sshPort/sshUser/sshTimeout/backendTimeout/xrayGrpcPort 都是 NOT NULL,
     * 这里直接拆箱传值, 不再 fallback (entity 层若出现 null 视为 DB 数据异常)。
     */
    default ServerCredentialDTO toCredential(ResourceServer e) {
        if (e == null) return null;
        return ServerCredentialDTO.builder()
                .serverId(e.getId())
                .sshHost(e.getHost())
                .sshPort(e.getSshPort())
                .sshUser(e.getSshUser())
                .sshPassword(e.getSshPassword())
                .sshPrivateKey(e.getSshPrivateKey())
                .sshTimeoutSeconds(e.getSshTimeoutSeconds())
                .xrayGrpcHost(e.getXrayGrpcHost())
                .xrayGrpcPort(e.getXrayGrpcPort())
                .backendTimeoutSeconds(e.getBackendTimeoutSeconds())
                .build();
    }
}
