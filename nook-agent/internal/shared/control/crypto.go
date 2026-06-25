package control

import "nook-agent/internal/shared/wirecrypto"

// 加密信封头; 必须与后台 AgentControlCrypto (ENC_HEADER / ENC_VALUE) 逐字节一致.
// 实际加解密算法在 wirecrypto (agent↔后台双向共用).
const (
	encHeader = "X-Agent-Enc"
	encValue  = "aes-gcm"
)

// decryptControlBody 解后台下发的 AES-GCM 信封 (含时间戳新鲜度校验); 「能解密」即证明对端持有 token = 鉴权.
func decryptControlBody(b64 []byte, token string) ([]byte, error) {
	return wirecrypto.Decrypt(b64, token)
}
