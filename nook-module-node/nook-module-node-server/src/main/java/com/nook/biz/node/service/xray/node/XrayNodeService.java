package com.nook.biz.node.service.xray.node;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.common.web.response.PageResult;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Xray 节点 Service —— xray 实例配置 + 运行时锚点 (last_xray_uptime) 的读写.
 *
 * @author nook
 */
public interface XrayNodeService {

    /**
     * 部署成功后初始化 / 更新 xray 节点配置, 幂等.
     *
     * <p>重装会覆写 installedAt = NOW + 清空 lastXrayUptime; touchdownSize 缩容时校验现有客户数,
     * 超过新上限直接抛 TOUCHDOWN_SHRINK_BLOCKED.
     *
     * @param serverId       关联 resource_server.id
     * @param xrayVersion    实际安装的 xray 版本 (latest 应已被入口侧解析成具体版本号)
     * @param xrayApiPort    xray 内置 api server 端口 (loopback)
     * @param xrayInstallDir xray 安装根目录
     * @param xrayBinaryPath xray binary 绝对路径 (落库给前端展示)
     * @param xrayConfigPath xray config.json 绝对路径 (落库给前端展示)
     * @param xrayShareDir   xray share 目录 (geo 数据, 落库给前端展示)
     * @param xrayLogDir     日志目录
     * @param touchdownSize  该 server 最多挂载落地 IP 数量 (软上限)
     */
    void upsertXrayNode(String serverId,
                        String xrayVersion,
                        int xrayApiPort,
                        String xrayInstallDir,
                        String xrayBinaryPath,
                        String xrayConfigPath,
                        String xrayShareDir,
                        String xrayLogDir,
                        int touchdownSize,
                        String protocol,
                        String transport,
                        String listenIp,
                        int sharedInboundPort,
                        String wsPath,
                        String domain,
                        String tlsCertPath,
                        String tlsKeyPath);

    /**
     * 按 serverId 查 Xray 节点; 必查到走 {@link com.nook.biz.node.validator.XrayNodeValidator#validateExists}.
     *
     * @param serverId resource_server.id
     * @return Xray 节点; 不存在返 null
     */
    XrayNodeDO getXrayNode(String serverId);

    /**
     * 按 serverId 批量取 xray 节点; 用于 controller enrich (按需取 sharedInboundPort 等 inbound 维度字段).
     *
     * @param serverIds resource_server.id 集合
     * @return serverId → XrayNodeDO; 未装 xray 的 server 自然不在 map 内, 由 caller 兜 null
     */
    Map<String, XrayNodeDO> getXrayNodeMap(Collection<String> serverIds);

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
