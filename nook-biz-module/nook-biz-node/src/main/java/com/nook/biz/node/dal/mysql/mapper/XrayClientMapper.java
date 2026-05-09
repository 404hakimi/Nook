package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xray client DB 访问层.
 *
 * @author nook
 */
@Mapper
public interface XrayClientMapper extends BaseMapper<XrayClientDO> {

    /** 按 (memberUserId, ipId) 查现有记录; 同一会员同一 IP 唯一映射, 存在表示已 provision. */
    default XrayClientDO selectByMemberAndIp(String memberUserId, String ipId) {
        return selectOne(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getMemberUserId, memberUserId)
                .eq(XrayClientDO::getIpId, ipId)
                .last("LIMIT 1"));
    }

    /** 按 (serverId, externalInboundRef, clientEmail) 唯一定位远端 client. */
    default XrayClientDO selectByEmail(String serverId, String externalInboundRef, String clientEmail) {
        return selectOne(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId)
                .eq(XrayClientDO::getExternalInboundRef, externalInboundRef)
                .eq(XrayClientDO::getClientEmail, clientEmail)
                .last("LIMIT 1"));
    }

    /** 列指定 server + inbound 下的所有 client (给 reconciler 用). */
    default List<XrayClientDO> selectByServerAndInbound(String serverId, String externalInboundRef) {
        return selectList(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId)
                .eq(XrayClientDO::getExternalInboundRef, externalInboundRef));
    }

    /** 列指定 server 下所有 client (reconciler 全量重写 xray.json 用, 不限 inbound tag). */
    default List<XrayClientDO> selectByServerId(String serverId) {
        return selectList(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId));
    }

    /** 更新 status + last_synced_at. */
    default int updateStatus(String id, Integer status, LocalDateTime syncedAt) {
        return update(null, Wrappers.<XrayClientDO>lambdaUpdate()
                .set(XrayClientDO::getStatus, status)
                .set(ObjectUtil.isNotNull(syncedAt), XrayClientDO::getLastSyncedAt, syncedAt)
                .eq(XrayClientDO::getId, id));
    }

    /** 更新 client_uuid (轮换密钥). */
    default int updateClientUuid(String id, String newUuid) {
        return update(null, Wrappers.<XrayClientDO>lambdaUpdate()
                .set(XrayClientDO::getClientUuid, newUuid)
                .eq(XrayClientDO::getId, id));
    }

    /** 列表分页, 多条件过滤. */
    default IPage<XrayClientDO> selectPageByQuery(IPage<XrayClientDO> page, ClientPageReqVO reqVO) {
        return selectPage(page, Wrappers.<XrayClientDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getServerId()), XrayClientDO::getServerId, reqVO.getServerId())
                .eq(StrUtil.isNotBlank(reqVO.getMemberUserId()), XrayClientDO::getMemberUserId, reqVO.getMemberUserId())
                .eq(StrUtil.isNotBlank(reqVO.getIpId()), XrayClientDO::getIpId, reqVO.getIpId())
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), XrayClientDO::getStatus, reqVO.getStatus())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), XrayClientDO::getClientEmail, reqVO.getKeyword())
                .orderByDesc(XrayClientDO::getCreatedAt));
    }
}
