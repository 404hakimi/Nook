package com.nook.biz.node.service.xray.node;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.common.web.response.PageResult;

import java.time.LocalDateTime;

/**
 * Xray 节点 Service 接口
 *
 * <p>xray_node 表代表 server 上的 xray 实例配置 + 运行时锚点 (last_xray_uptime).
 *
 * @author nook
 */
public interface XrayNodeService {

    /**
     * 部署成功后初始化 / 更新 xray 节点配置 + slot 池, 幂等
     *
     * <p>同事务保证 "xray_node 行存在 ↔ slot 池已初始化"; 重装会覆写 installedAt = NOW + 清空 lastXrayUptime
     * (旧 uptime 已无效, 等 reconciler 重新探测填).
     *
     * @param serverId       关联 resource_server.id
     * @param xrayVersion    实际安装的 xray 版本 (latest 应已被入口侧解析成具体版本号)
     * @param xrayApiPort    xray 内置 api server 端口 (loopback)
     * @param xrayInstallDir xray 安装根目录
     * @param xrayLogDir     日志目录
     * @param slotPoolSize   slot 池大小
     * @param slotPortBase   slot 端口段起点
     */
    void upsertXrayNode(String serverId,
                        String xrayVersion,
                        int xrayApiPort,
                        String xrayInstallDir,
                        String xrayLogDir,
                        int slotPoolSize,
                        int slotPortBase);

    /**
     * 获得 Xray 节点, 不存在抛 SERVER_STATE_NOT_FOUND
     *
     * @param serverId resource_server.id
     * @return Xray 节点
     */
    XrayNodeDO getXrayNode(String serverId);

    /**
     * 获得 Xray 节点, 不存在返回 null (用于判断 server 是否已装 xray)
     *
     * @param serverId resource_server.id
     * @return Xray 节点 或 null
     */
    XrayNodeDO getXrayNodeOrNull(String serverId);

    /**
     * 标记 replay 已完成, 更新 last_xray_uptime + updated_at
     *
     * @param serverId   resource_server.id
     * @param xrayUptime 当前探测到的 xray 启动时间
     */
    void markReplayDone(String serverId, LocalDateTime xrayUptime);

    /**
     * 分页查询 Xray 节点
     *
     * @param pageReqVO 分页条件
     * @return 分页结果
     */
    PageResult<XrayNodeDO> getXrayNodePage(XrayNodePageReqVO pageReqVO);
}
