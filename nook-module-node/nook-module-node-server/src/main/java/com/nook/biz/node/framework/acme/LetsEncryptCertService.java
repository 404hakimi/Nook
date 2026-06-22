package com.nook.biz.node.framework.acme;

import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.framework.cloudflare.CloudflareApiClient;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 后台 ACME (Let's Encrypt) 证书签发: 取代旧 on-server acme.sh, agent 不再直连 Cloudflare.
 * <p>流程: ACME 账号 (落盘私钥跨签发复用) → 下单 fqdn → DNS-01 挑战 (把 token digest 写成
 * {@code _acme-challenge.<fqdn>} TXT, 经 {@link CloudflareApiClient}) → 校验通过 → 用新生成的域名私钥
 * finalize → 下载全链证书. 产物 {@link IssuedCert} 落库, 部署时下发 agent 写盘.
 *
 * <p>本类只签发; 复用判定 (>N 天有效则不重签) 与持久化由调用方负责.
 *
 * @author nook
 */
@Slf4j
@Component
public class LetsEncryptCertService {

    /** ACME 目录 URL; 默认 LE 正式环境, 联调可切 {@code acme://letsencrypt.org/staging} 避免限流. */
    @Value("${nook.acme.directory-url:acme://letsencrypt.org}")
    private String directoryUrl;

    /** ACME 账号私钥落盘路径; 跨签发复用同一 LE 账号, 不存在则首次生成. */
    @Value("${nook.acme.account-key-path:#{systemProperties['user.home'] + '/.nook/acme/account.pem'}}")
    private String accountKeyPath;

    /** 写完 TXT 到触发校验前的等待秒数, 留足 DNS 传播 (CF 通常很快, 给冗余). */
    @Value("${nook.acme.dns-propagation-seconds:25}")
    private int dnsPropagationSeconds;

    /** 单个挑战 / order finalize 的轮询上限. */
    @Value("${nook.acme.poll-timeout-seconds:120}")
    private int pollTimeoutSeconds;

    @Resource
    private CloudflareApiClient cloudflareApiClient;

    /**
     * 为 fqdn 签发一张证书 (DNS-01 经 Cloudflare). 同步阻塞至签发完或失败.
     *
     * @param fqdn       完整域名 (e.g. server01.example.com)
     * @param cfApiToken 该域名所在 zone 的 Cloudflare API Token (与建 A 记录同源)
     * @return 全链证书 + 私钥 + 到期时间
     */
    public IssuedCert issue(String fqdn, String cfApiToken) {
        try {
            return doIssue(fqdn, cfApiToken);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[acme] 签发失败 fqdn={}", fqdn, e);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, fqdn,
                    "证书签发失败: " + e.getMessage());
        }
    }

    private IssuedCert doIssue(String fqdn, String cfApiToken) throws Exception {
        KeyPair accountKey = loadOrCreateAccountKey();
        Session session = new Session(directoryUrl);
        Account account = new AccountBuilder()
                .agreeToTermsOfService()
                .useKeyPair(accountKey)
                .create(session);
        log.info("[acme] 账号就绪 directory={} fqdn={}", directoryUrl, fqdn);

        Order order = account.newOrder().domain(fqdn).create();
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() != Status.VALID) {
                authorizeDns01(auth, cfApiToken);
            }
        }

        // 域名私钥每次新生成 (即证书私钥); finalize 时 acme4j 内部据此建 CSR
        KeyPair domainKey = KeyPairUtils.createKeyPair(2048);
        order.execute(domainKey);
        Status orderStatus = order.waitForCompletion(Duration.ofSeconds(pollTimeoutSeconds));
        if (orderStatus != Status.VALID) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, fqdn,
                    "ACME order 未完成: " + orderStatus);
        }

        Certificate certificate = order.getCertificate();
        String certPem = writePem(certificate::writeCertificate);
        String keyPem = writePem(w -> KeyPairUtils.writeKeyPair(domainKey, w));
        LocalDateTime notAfter = LocalDateTime.ofInstant(
                certificate.getCertificate().getNotAfter().toInstant(), ZoneId.systemDefault());
        log.info("[acme] 签发完成 fqdn={} notAfter={}", fqdn, notAfter);
        return IssuedCert.builder().certPem(certPem).keyPem(keyPem).notAfter(notAfter).build();
    }

    /** 单个授权的 DNS-01 挑战: 写 TXT → 等传播 → 触发 → 轮询; 无论成败都清理 TXT. */
    private void authorizeDns01(Authorization auth, String cfApiToken) throws Exception {
        String authDomain = auth.getIdentifier().getDomain();
        Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.class)
                .orElseThrow(() -> new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, authDomain,
                        "该授权无 DNS-01 挑战 (检查 zone 是否托管在 Cloudflare)"));

        String recordId = cloudflareApiClient.upsertTxtRecord(cfApiToken, authDomain, challenge.getDigest());
        try {
            TimeUnit.SECONDS.sleep(dnsPropagationSeconds);
            challenge.trigger();
            Status status = auth.waitForCompletion(Duration.ofSeconds(pollTimeoutSeconds));
            if (status != Status.VALID) {
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, authDomain,
                        "DNS-01 校验失败: " + status);
            }
        } finally {
            cloudflareApiClient.deleteDnsRecord(cfApiToken, authDomain, recordId);
        }
    }

    /** 加载落盘的 ACME 账号私钥; 不存在则生成并写盘 (父目录自动创建). */
    private synchronized KeyPair loadOrCreateAccountKey() throws IOException {
        File file = new File(accountKeyPath);
        if (file.exists()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                return KeyPairUtils.readKeyPair(reader);
            }
        }
        KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            KeyPairUtils.writeKeyPair(keyPair, writer);
        }
        log.info("[acme] 生成并落盘新 ACME 账号私钥: {}", accountKeyPath);
        return keyPair;
    }

    /** 把 acme4j 的 PEM 写入收敛成 String. */
    private String writePem(PemSink sink) throws IOException {
        StringWriter sw = new StringWriter();
        sink.writeTo(sw);
        return sw.toString();
    }

    @FunctionalInterface
    private interface PemSink {
        void writeTo(Writer writer) throws IOException;
    }
}
