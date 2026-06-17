package com.nook.biz.node.framework.xray.inbound.config;

import cn.hutool.core.util.HexUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.Base64;

/**
 * REALITY x25519 密钥对 + shortId 生成器; 后台 JDK 生成, base64url/hex 对齐 xray x25519 输出格式
 *
 * @author nook
 */
@Component
public class RealityKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * 生成 x25519 密钥对 (base64 raw url 无填充)
     *
     * @return 私钥 + 公钥
     */
    public RealityKeyPair generateKeyPair() {
        KeyPair keyPair = this.newX25519KeyPair();
        XECPrivateKey priv = (XECPrivateKey) keyPair.getPrivate();
        XECPublicKey pub = (XECPublicKey) keyPair.getPublic();
        // 私钥取原始 scalar 32 字节; 公钥 u 坐标转 32 字节小端 (wire 格式)
        byte[] privBytes = priv.getScalar()
                .orElseThrow(() -> new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        "<reality>", "x25519 私钥提取失败"));
        byte[] pubBytes = this.toLittleEndian32(pub.getU());
        return new RealityKeyPair(B64_URL.encodeToString(privBytes), B64_URL.encodeToString(pubBytes));
    }

    /**
     * 生成一个 shortId (hex)
     *
     * @param byteLen 字节数 (≤8; hex 长度 = 2×byteLen)
     * @return hex 字符串
     */
    public String generateShortId(int byteLen) {
        byte[] buf = new byte[byteLen];
        RANDOM.nextBytes(buf);
        return HexUtil.encodeHexStr(buf);
    }

    private KeyPair newX25519KeyPair() {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    "<reality>", "JDK 不支持 X25519");
        }
    }

    /** X25519 公钥 u 坐标转 32 字节小端; BigInteger 是大端有符号, 反转并定长到 32. */
    private byte[] toLittleEndian32(BigInteger u) {
        byte[] be = u.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < be.length && i < 32; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }

    /**
     * REALITY x25519 密钥对 (base64url)
     *
     * @author nook
     */
    public record RealityKeyPair(String privateKey, String publicKey) {
    }
}
