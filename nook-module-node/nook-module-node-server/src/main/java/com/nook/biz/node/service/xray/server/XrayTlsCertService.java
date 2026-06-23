package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.entity.XrayTlsCertDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xray TLS 证书 Service; 证书与 xray_install 解耦后的独立持久化 (xray_tls_cert 表)
 *
 * @author nook
 */
public interface XrayTlsCertService {

    /**
     * 按 serverId 取证书; 无则 null
     *
     * @param serverId 服务器编号
     * @return 证书 DO 或 null
     */
    XrayTlsCertDO get(String serverId);

    /**
     * 幂等落库证书 (签发/续期后)
     *
     * @param serverId 服务器编号
     * @param fqdn     证书 FQDN
     * @param certPem  全链证书 PEM
     * @param keyPem   私钥 PEM
     * @param notAfter 叶子证书到期时间
     */
    void save(String serverId, String fqdn, String certPem, String keyPem, LocalDateTime notAfter);

    /**
     * 删除证书 (重新部署成非 TLS 时); 不存在视为幂等通过
     *
     * @param serverId 服务器编号
     */
    void clear(String serverId);

    /**
     * 列出临期证书 (notAfter < before); 续期任务扫描用
     *
     * @param before 到期时间阈值 (= now + 续期窗口)
     * @return 命中的证书列表
     */
    List<XrayTlsCertDO> listExpiring(LocalDateTime before);
}
