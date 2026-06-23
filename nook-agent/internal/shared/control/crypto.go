package control

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"time"
)

// 加密信封头 + 域分离标签; 必须与后台 ControlCrypto (ENC_HEADER / ENC_VALUE / LABEL) 逐字节一致.
const (
	encHeader = "X-Agent-Enc"
	encValue  = "aes-gcm"
	encLabel  = "nook-agent-control-v1"
)

// 时间戳容忍窗口 (秒): 解密后明文前 8B 是后台毫秒时间戳, 偏差超此判重放/时钟漂移. 双端须 NTP 同步.
const maxSkewSeconds = 300

// decryptControlBody 解后台 AES-256-GCM 信封并校验新鲜度: 输入 base64(nonce ‖ 密文+tag),
// key = SHA-256(LABEL ‖ token); 「能解密」即证明对端持有 token = 鉴权. 明文帧 = [8B 毫秒时间戳][原文].
func decryptControlBody(b64 []byte, token string) ([]byte, error) {
	raw, err := base64.StdEncoding.DecodeString(string(b64))
	if err != nil {
		return nil, err
	}
	key := sha256.Sum256(append([]byte(encLabel), []byte(token)...))
	block, err := aes.NewCipher(key[:])
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
		return nil, err // 认证失败 = token 不符 / 篡改
	}
	if len(framed) < 8 {
		return nil, errors.New("载荷缺时间戳")
	}
	tsMs := int64(binary.BigEndian.Uint64(framed[:8]))
	skewMs := time.Now().UnixMilli() - tsMs
	if skewMs < 0 {
		skewMs = -skewMs
	}
	if skewMs > maxSkewSeconds*1000 {
		return nil, fmt.Errorf("时间戳偏差 %ds 超窗 (重放或时钟漂移?)", skewMs/1000)
	}
	return framed[8:], nil
}
