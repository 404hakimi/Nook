package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.XrayInstallDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Xray 实例元数据 Mapper
 *
 * @author nook
 */
@Mapper
public interface XrayInstallMapper extends BaseMapper<XrayInstallDO> {

    default boolean existsBySubdomain(String domainId, String subdomain, String excludeServerId) {
        return exists(Wrappers.<XrayInstallDO>lambdaQuery()
                .eq(XrayInstallDO::getDomainId, domainId)
                .eq(XrayInstallDO::getSubdomain, subdomain)
                .ne(XrayInstallDO::getServerId, excludeServerId));
    }

    default int updateInstallStatus(String serverId, String statusCode, boolean markInstalled) {
        return update(null, Wrappers.<XrayInstallDO>lambdaUpdate()
                .set(XrayInstallDO::getInstallStatus, statusCode)
                .set(markInstalled, XrayInstallDO::getInstalledAt, LocalDateTime.now())
                .set(XrayInstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(XrayInstallDO::getServerId, serverId));
    }

    default void clearTlsBinding(String serverId) {
        update(null, Wrappers.<XrayInstallDO>lambdaUpdate()
                .set(XrayInstallDO::getDomainId, null)
                .set(XrayInstallDO::getSubdomain, null)
                .set(XrayInstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(XrayInstallDO::getServerId, serverId));
    }
}
