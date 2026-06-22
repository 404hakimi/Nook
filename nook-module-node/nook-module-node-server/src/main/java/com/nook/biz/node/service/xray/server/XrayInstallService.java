package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.entity.XrayInstallDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Xray 实例元数据 Service 接口
 *
 * @author nook
 */
public interface XrayInstallService {

    /**
     * 幂等写入实例元数据
     *
     * @param entity 实例元数据
     */
    void upsert(XrayInstallDO entity);

    /**
     * 按 serverId 取实例元数据
     *
     * @param serverId 服务器编号
     * @return 实例元数据
     */
    XrayInstallDO get(String serverId);

    /**
     * 同一根域下二级标签是否已被别的线路机占用 (重装时排除自身)
     *
     * @param domainId        根域 system_domain.id
     * @param subdomain       二级标签
     * @param excludeServerId 排除的服务器编号 (当前机)
     * @return true = 已被其他机占用
     */
    boolean isSubdomainTaken(String domainId, String subdomain, String excludeServerId);

    /**
     * 标记 replay 完成
     *
     * @param serverId   服务器编号
     * @param xrayUptime 探测到的 xray 启动时间
     */
    void markReplayDone(String serverId, LocalDateTime xrayUptime);

    /**
     * 更新装机状态 (agent 回报后); OK 时同步置 installedAt = now
     *
     * @param serverId 服务器编号
     * @param status   装机状态
     */
    void markInstallStatus(String serverId, XrayInstallStatusEnum status);

    /**
     * 落库后台签发的 TLS 证书 (定向更新, 不碰其它列)
     *
     * @param serverId 服务器编号
     * @param certPem  全链证书 PEM
     * @param keyPem   私钥 PEM
     * @param notAfter 叶子证书到期时间
     */
    void saveTlsCert(String serverId, String certPem, String keyPem, LocalDateTime notAfter);

    /**
     * 清空该 server 的 TLS 绑定 (域名 + 证书列); 重新部署成非 TLS 时调,
     * 避免全局 NOT_NULL 更新策略把旧证书 / 旧域名残留在行里 (误导详情页, 也防未来续期任务误扫).
     *
     * @param serverId 服务器编号
     */
    void clearTlsBinding(String serverId);

    /**
     * 列出临期可续证书的实例 (已签 && notAfter < before && 装机 ok); 续期任务用
     *
     * @param before 到期时间阈值 (= now + 续期窗口)
     * @return 命中的实例元数据列表
     */
    List<XrayInstallDO> listRenewable(LocalDateTime before);
}
