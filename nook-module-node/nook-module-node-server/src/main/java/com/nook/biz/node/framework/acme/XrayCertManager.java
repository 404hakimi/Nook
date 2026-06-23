package com.nook.biz.node.framework.acme;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.entity.XrayTlsCertDO;
import com.nook.biz.node.service.xray.server.XrayTlsCertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * xray TLS 证书的"取或签": 桥接持久化 ({@link XrayTlsCertService} 独立证书表) 与签发 ({@link LetsEncryptCertService}).
 * <p>复用判定: 库内证书 PEM/私钥齐全 && 覆盖目标 fqdn && 剩余有效期 &gt; {@code renewBeforeDays} 天 → 直接复用,
 * 否则签发并落库 (沿用旧 acme.sh "临期才重签"省 Let's Encrypt 配额的思路). 续期任务亦复用本类.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayCertManager {

    /** 剩余有效期低于此天数则重签 (= 续期窗口); 复用判定同此阈值. */
    @Value("${nook.acme.renew-before-days:30}")
    private int renewBeforeDays;

    @Resource
    private XrayTlsCertService xrayTlsCertService;
    @Resource
    private LetsEncryptCertService letsEncryptCertService;

    /** per-fqdn 单飞锁: 同一域名并发签发会重复下单 (浪费 LE 配额) 且 DNS-01 TXT 互踩, 串行化之. 不清理 (上限 ≈ 服务器数). */
    private final ConcurrentHashMap<String, Object> issueLocks = new ConcurrentHashMap<>();

    /**
     * 取该 server 当前可用的 TLS 证书: 库内可复用则复用, 否则签发并落库. 同一 fqdn 串行 (单飞).
     *
     * @param serverId   线路机 ID
     * @param fqdn       完整域名
     * @param cfApiToken 该域名 zone 的 Cloudflare API Token (DNS-01 用)
     * @param progress   进度回调 (透到部署流, 打破签发期静默); 可空
     * @return 全链证书 + 私钥 + 到期时间
     */
    public IssuedCert ensureCert(String serverId, String fqdn, String cfApiToken, Consumer<String> progress) {
        Object lock = issueLocks.computeIfAbsent(fqdn, k -> new Object());
        synchronized (lock) {
            XrayTlsCertDO row = xrayTlsCertService.get(serverId);
            if (row != null && canReuse(row, fqdn)) {
                log.info("[acme] 复用库内证书 server={} fqdn={} notAfter={}", serverId, fqdn, row.getNotAfter());
                if (progress != null) {
                    progress.accept("复用库内有效证书 (到期 " + row.getNotAfter() + ")");
                }
                return IssuedCert.builder()
                        .certPem(row.getCertPem())
                        .keyPem(row.getKeyPem())
                        .notAfter(row.getNotAfter())
                        .build();
            }
            IssuedCert issued = letsEncryptCertService.issue(fqdn, cfApiToken, progress);
            xrayTlsCertService.save(serverId, fqdn, issued.getCertPem(), issued.getKeyPem(), issued.getNotAfter());
            return issued;
        }
    }

    /** 库内证书是否可复用: PEM/私钥齐 && 未临期 && 覆盖 fqdn. */
    private boolean canReuse(XrayTlsCertDO row, String fqdn) {
        if (StrUtil.isBlank(row.getCertPem()) || StrUtil.isBlank(row.getKeyPem())
                || row.getNotAfter() == null) {
            return false;
        }
        if (row.getNotAfter().isBefore(LocalDateTime.now().plusDays(renewBeforeDays))) {
            return false; // 临期 → 重签
        }
        return certCoversDomain(row.getCertPem(), fqdn); // 域名变了 / 证书坏了 → 重签
    }

    /** 解析全链 PEM 的叶子证书 (首张), 判其 SAN (dNSName) 是否覆盖 fqdn; 解析失败按"不可复用"处理 (LE 证书只填 SAN, 不看 CN). */
    private boolean certCoversDomain(String certPem, String fqdn) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate leaf = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8)));
            Collection<List<?>> sans = leaf.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> san : sans) {
                    // [0]=类型 (2=dNSName), [1]=值
                    if (san.size() >= 2 && Integer.valueOf(2).equals(san.get(0))
                            && dnsMatches(String.valueOf(san.get(1)), fqdn)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("[acme] 解析库内证书失败, 将重签 fqdn={}: {}", fqdn, e.getMessage());
            return false;
        }
    }

    /** dNSName 匹配: 精确 (忽略大小写) 或 *.x 通配命中 fqdn 的父域. */
    private boolean dnsMatches(String dnsName, String fqdn) {
        if (dnsName.equalsIgnoreCase(fqdn)) {
            return true;
        }
        if (dnsName.startsWith("*.")) {
            int dot = fqdn.indexOf('.');
            return dot > 0 && dnsName.substring(2).equalsIgnoreCase(fqdn.substring(dot + 1));
        }
        return false;
    }
}
