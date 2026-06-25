// Package wirecrypto: agent ↔ 后台 双向对称载荷加密 (AES-256-GCM, key 由 agent_token 本地派生, token 不过线).
//
// 后台→agent: 加密 /xray/deploy、/xray/cert 的 body (control 包解). agent→后台: 加密鉴权证明 (X-Agent-Auth, client 包用).
// 算法/标签/分段必须与后台 AgentControlCrypto 严格一致: key = SHA-256(Label ‖ token), 明文前置 8B big-endian
// 毫秒时间戳防重放, 信封 = base64(nonce(12B) ‖ 密文(含时间戳)+GCM tag(16B)).
package wirecrypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"time"
)

// Label 域分离标签; 与后台 AgentControlCrypto.LABEL 逐字节一致.
const Label = "nook-agent-control-v1"

// MaxSkewSeconds 时间戳容忍窗口 (秒); 与后台一致. 双端须 NTP 同步 (agent 启动 bootstrap 已开 NTP).
const MaxSkewSeconds = 300

func deriveKey(token string) [32]byte {
	return sha256.Sum256(append([]byte(Label), []byte(token)...))
}

// Encrypt: 明文前置 8B 毫秒时间戳后 AES-256-GCM 加密; 返回 base64(nonce ‖ 密文+tag).
func Encrypt(plaintext, token string) (string, error) {
	k := deriveKey(token)
	block, err := aes.NewCipher(k[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	framed := make([]byte, 8+len(plaintext))
	binary.BigEndian.PutUint64(framed[:8], uint64(time.Now().UnixMilli()))
	copy(framed[8:], plaintext)
	ciphertext := gcm.Seal(nil, nonce, framed, nil)
	return base64.StdEncoding.EncodeToString(append(nonce, ciphertext...)), nil
}

// Decrypt: 解 base64(nonce ‖ 密文+tag) + 校验时间戳新鲜度, 返回 payload (去时间戳).
// GCM 认证失败 (token 不符/篡改) 或时间戳超窗 (重放) 返 error.
func Decrypt(b64 []byte, token string) ([]byte, error) {
	raw, err := base64.StdEncoding.DecodeString(string(b64))
	if err != nil {
		return nil, err
	}
	k := deriveKey(token)
	block, err := aes.NewCipher(k[:])
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	if len(raw) < gcm.NonceSize() {
		return nil, errors.New("加密载荷过短")
	}
	nonce, ciphertext := raw[:gcm.NonceSize()], raw[gcm.NonceSize():]
	framed, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, err
	}
	if len(framed) < 8 {
		return nil, errors.New("载荷缺时间戳")
	}
	tsMs := int64(binary.BigEndian.Uint64(framed[:8]))
	skewMs := time.Now().UnixMilli() - tsMs
	if skewMs < 0 {
		skewMs = -skewMs
	}
	if skewMs > MaxSkewSeconds*1000 {
		return nil, fmt.Errorf("时间戳偏差 %ds 超窗 (重放或时钟漂移?)", skewMs/1000)
	}
	return framed[8:], nil
}
