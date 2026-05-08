package com.nook.biz.xray.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundPageReqVO;
import com.nook.biz.xray.entity.XrayInbound;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface XrayInboundMapper extends BaseMapper<XrayInbound> {

    /** 按 (memberId, ipId) 查现有记录；同一会员同一 IP 唯一映射，存在表示已 provision。 */
    default XrayInbound selectByMemberAndIp(String memberUserId, String ipId) {
        return selectOne(Wrappers.<XrayInbound>lambdaQuery()
                .eq(XrayInbound::getMemberUserId, memberUserId)
                .eq(XrayInbound::getIpId, ipId)
                .last("LIMIT 1"));
    }

    /** 按 (serverId, externalInboundRef, clientEmail) 唯一定位远端 client。 */
    default XrayInbound selectByEmail(String serverId, String externalInboundRef, String clientEmail) {
        return selectOne(Wrappers.<XrayInbound>lambdaQuery()
                .eq(XrayInbound::getServerId, serverId)
                .eq(XrayInbound::getExternalInboundRef, externalInboundRef)
                .eq(XrayInbound::getClientEmail, clientEmail)
                .last("LIMIT 1"));
    }

    /** 列指定 server + inbound 下的所有 client(给 reconciler 用)。 */
    default List<XrayInbound> selectByServerAndInbound(String serverId, String externalInboundRef) {
        return selectList(Wrappers.<XrayInbound>lambdaQuery()
                .eq(XrayInbound::getServerId, serverId)
                .eq(XrayInbound::getExternalInboundRef, externalInboundRef));
    }

    /** 更新 status + last_synced_at。 */
    default int updateStatus(String id, Integer status, LocalDateTime syncedAt) {
        return update(null, Wrappers.<XrayInbound>lambdaUpdate()
                .set(XrayInbound::getStatus, status)
                .set(ObjectUtil.isNotNull(syncedAt), XrayInbound::getLastSyncedAt, syncedAt)
                .eq(XrayInbound::getId, id));
    }

    /** 更新 client_uuid(轮换密钥)。 */
    default int updateClientUuid(String id, String newUuid) {
        return update(null, Wrappers.<XrayInbound>lambdaUpdate()
                .set(XrayInbound::getClientUuid, newUuid)
                .eq(XrayInbound::getId, id));
    }

    /** 列表分页，多条件过滤。 */
    default IPage<XrayInbound> selectPageByQuery(IPage<XrayInbound> page, XrayInboundPageReqVO reqVO) {
        return selectPage(page, Wrappers.<XrayInbound>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getServerId()), XrayInbound::getServerId, reqVO.getServerId())
                .eq(StrUtil.isNotBlank(reqVO.getMemberUserId()), XrayInbound::getMemberUserId, reqVO.getMemberUserId())
                .eq(StrUtil.isNotBlank(reqVO.getIpId()), XrayInbound::getIpId, reqVO.getIpId())
                .eq(StrUtil.isNotBlank(reqVO.getBackendType()), XrayInbound::getBackendType, reqVO.getBackendType())
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), XrayInbound::getStatus, reqVO.getStatus())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), XrayInbound::getClientEmail, reqVO.getKeyword())
                .orderByDesc(XrayInbound::getCreatedAt));
    }
}
