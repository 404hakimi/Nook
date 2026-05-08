package com.nook.biz.xray.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.xray.controller.client.vo.XrayClientPageReqVO;
import com.nook.biz.xray.entity.XrayClient;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface XrayClientMapper extends BaseMapper<XrayClient> {

    /** 按 (memberId, ipId) 查现有记录；同一会员同一 IP 唯一映射，存在表示已 provision。 */
    default XrayClient selectByMemberAndIp(String memberUserId, String ipId) {
        return selectOne(Wrappers.<XrayClient>lambdaQuery()
                .eq(XrayClient::getMemberUserId, memberUserId)
                .eq(XrayClient::getIpId, ipId)
                .last("LIMIT 1"));
    }

    /** 按 (serverId, externalInboundRef, clientEmail) 唯一定位远端 client。 */
    default XrayClient selectByEmail(String serverId, String externalInboundRef, String clientEmail) {
        return selectOne(Wrappers.<XrayClient>lambdaQuery()
                .eq(XrayClient::getServerId, serverId)
                .eq(XrayClient::getExternalInboundRef, externalInboundRef)
                .eq(XrayClient::getClientEmail, clientEmail)
                .last("LIMIT 1"));
    }

    /** 列指定 server + inbound 下的所有 client(给 reconciler 用)。 */
    default List<XrayClient> selectByServerAndInbound(String serverId, String externalInboundRef) {
        return selectList(Wrappers.<XrayClient>lambdaQuery()
                .eq(XrayClient::getServerId, serverId)
                .eq(XrayClient::getExternalInboundRef, externalInboundRef));
    }

    /** 列指定 server 下所有 client(reconciler 全量重写 xray.json 用, 不限 inbound tag)。 */
    default List<XrayClient> selectByServerId(String serverId) {
        return selectList(Wrappers.<XrayClient>lambdaQuery()
                .eq(XrayClient::getServerId, serverId));
    }

    /** 更新 status + last_synced_at。 */
    default int updateStatus(String id, Integer status, LocalDateTime syncedAt) {
        return update(null, Wrappers.<XrayClient>lambdaUpdate()
                .set(XrayClient::getStatus, status)
                .set(ObjectUtil.isNotNull(syncedAt), XrayClient::getLastSyncedAt, syncedAt)
                .eq(XrayClient::getId, id));
    }

    /** 更新 client_uuid(轮换密钥)。 */
    default int updateClientUuid(String id, String newUuid) {
        return update(null, Wrappers.<XrayClient>lambdaUpdate()
                .set(XrayClient::getClientUuid, newUuid)
                .eq(XrayClient::getId, id));
    }

    /** 列表分页，多条件过滤。 */
    default IPage<XrayClient> selectPageByQuery(IPage<XrayClient> page, XrayClientPageReqVO reqVO) {
        return selectPage(page, Wrappers.<XrayClient>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getServerId()), XrayClient::getServerId, reqVO.getServerId())
                .eq(StrUtil.isNotBlank(reqVO.getMemberUserId()), XrayClient::getMemberUserId, reqVO.getMemberUserId())
                .eq(StrUtil.isNotBlank(reqVO.getIpId()), XrayClient::getIpId, reqVO.getIpId())
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), XrayClient::getStatus, reqVO.getStatus())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), XrayClient::getClientEmail, reqVO.getKeyword())
                .orderByDesc(XrayClient::getCreatedAt));
    }
}
