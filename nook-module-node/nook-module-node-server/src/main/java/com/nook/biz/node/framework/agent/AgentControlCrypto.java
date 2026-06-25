package com.nook.biz.node.framework.agent;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * agent 控制接口载荷加密: 通道 (:44844) 是 UFW 限受信网络的明文 HTTP, 但 /xray/deploy、/xray/cert
 * 会带 TLS 私钥过线, 故对请求体做 AES-256-GCM 端到端加密 (agent 侧用同算法解).
 *
 * <p><b>token 不再明文过线 (双向)</b>: 密钥 = SHA-256(LABEL ‖ agentToken) 由共享 token 本地派生, token 不进任何请求头.
 * 后台→agent: 加密 body, agent「能解密」即鉴权. agent→后台: agent 用 {@link #encrypt} 加密 serverId 当鉴权证明
 * (X-Agent-Auth), 后台按明文 serverId 找 token + {@link #decrypt} 解证明即鉴权 (见 ResourceServerApi#verifyAgentAuth).
 * 明文帧前置 8 字节 big-endian 毫秒时间戳防重放. 信封 = base64(nonce(12B) ‖ 密文(含时间戳)+GCM tag(16B)).
 * 双端算法/标签/分段必须严格一致 (见 agent shared/control/crypto.go).
 *
 * @author nook
 */
public final class AgentControlCrypto {

    /** 域分离标签; agent 侧 encLabel 必须逐字节一致, 改这里要同步改 agent. */
    static final String LABEL = "nook-agent-control-v1";
    /** 加密信封头; 带此头表示 body 是加密信封, agent 据此决定是否解密. */
    static final String ENC_HEADER = "X-Agent-Enc";
    static final String ENC_VALUE = "aes-gcm";

    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    /** 时间戳容忍窗口 (毫秒); 与 agent maxSkewSeconds 一致. */
    private static final long MAX_SKEW_MILLIS = 300_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentControlCrypto() {
    }

    /** AES-256-GCM 加密 (明文前置 8B 时间戳防重放); 返回 base64(nonce ‖ 密文+tag). */
    public static String encrypt(String plaintext, String token) {
        try {
            byte[] nonce = new byte[NONCE_LEN];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(token), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] body = plaintext.getBytes(StandardCharsets.UTF_8);
            // 帧 = [8B big-endian 毫秒时间戳][原文]; 时间戳进密文受 GCM 完整性保护, agent 校验新鲜度
            byte[] framed = ByteBuffer.allocate(Long.BYTES + body.length)
                    .putLong(System.currentTimeMillis()).put(body).array();
            byte[] ciphertext = cipher.doFinal(framed);
            byte[] envelope = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("控制接口载荷加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解 base64(nonce ‖ 密文+tag): GCM 认证 (token 不符则失败) + 校验时间戳新鲜度 (防重放), 返回 payload (去时间戳).
     * 任一步失败 (认证/过期/格式) 抛 IllegalStateException; 调用方据此判鉴权失败.
     */
    public static String decrypt(String envelopeB64, String token) {
        try {
            byte[] raw = Base64.getDecoder().decode(envelopeB64);
            if (raw.length < NONCE_LEN) {
                throw new IllegalStateException("加密信封过短");
            }
            byte[] nonce = new byte[NONCE_LEN];
            System.arraycopy(raw, 0, nonce, 0, NONCE_LEN);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(token), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] framed = cipher.doFinal(raw, NONCE_LEN, raw.length - NONCE_LEN);
            if (framed.length < Long.BYTES) {
                throw new IllegalStateException("载荷缺时间戳");
            }
            long ts = ByteBuffer.wrap(framed, 0, Long.BYTES).getLong();
            if (Math.abs(System.currentTimeMillis() - ts) > MAX_SKEW_MILLIS) {
                throw new IllegalStateException("时间戳超窗 (重放或时钟漂移?)");
            }
            return new String(framed, Long.BYTES, framed.length - Long.BYTES, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("解密失败 (token 不符 / 篡改): " + e.getMessage(), e);
        }
    }

    /** key = SHA-256(LABEL ‖ token) → 32 字节 (AES-256). */
    private static byte[] deriveKey(String token) throws GeneralSecurityException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(LABEL.getBytes(StandardCharsets.UTF_8));
        return md.digest(token.getBytes(StandardCharsets.UTF_8));
    }
}
