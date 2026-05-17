/**
 * 把一条 Xray client 配置拼成各客户端 (v2rayN / Clash / Shadowrocket / NekoBox 等) 都认识的标准 URI。
 *
 * 协议格式参考:
 *   - vmess: vmess://base64(json) — V2Ray 早期方案, JSON 字段 v/ps/add/port/id/aid/scy/net/type/host/path/tls
 *   - vless: vless://uuid@host:port?encryption=none&type=tcp&flow=...#remark
 *   - trojan: trojan://password@host:port?type=tcp#remark
 *   - shadowsocks: ss://base64(method:password)@host:port#remark (这里 client_uuid 复用 password 字段)
 *
 * 字段语义参考: https://github.com/2dust/v2rayN / https://xtls.github.io/document/
 */

export interface XrayClientUriInput {
  protocol: string
  host: string
  port: number
  uuid: string
  email: string
  /** vless 流控, 如 xtls-rprx-vision; 其他协议留空 */
  flow?: string
  /** 传输方式: tcp / ws / grpc / xhttp 等; 默认 tcp */
  transport?: string
  /** ws path; transport=ws 时必填 */
  wsPath?: string
  /** TLS 启用标志; true 时加 tls 参数 */
  tlsEnabled?: boolean
  /** TLS SNI (= 服务器域名); tlsEnabled=true 时必填 */
  sni?: string
}

export function buildClientUri(input: XrayClientUriInput): string {
  const proto = (input.protocol || '').toLowerCase()
  switch (proto) {
    case 'vmess':
      return buildVmess(input)
    case 'vless':
      return buildVless(input)
    case 'trojan':
      return buildTrojan(input)
    case 'shadowsocks':
    case 'ss':
      return buildShadowsocks(input)
    default:
      throw new Error(`不支持的协议: ${input.protocol}`)
  }
}

/** vmess JSON v2 schema; ws + 可选 TLS, path/sni 跟 server 配置对齐. */
function buildVmess(input: XrayClientUriInput): string {
  const payload = {
    v: '2',
    ps: input.email,
    add: input.host,
    port: input.port,
    id: input.uuid,
    aid: 0,
    scy: 'auto',
    net: input.transport || 'tcp',
    type: 'none',
    host: input.tlsEnabled ? (input.sni || '') : '',
    path: input.wsPath || '',
    tls: input.tlsEnabled ? 'tls' : '',
    sni: input.tlsEnabled ? (input.sni || '') : '',
    alpn: input.tlsEnabled ? 'h2,http/1.1' : '',
    fp: ''
  }
  return 'vmess://' + base64UrlSafe(JSON.stringify(payload))
}

function buildVless(input: XrayClientUriInput): string {
  const params = new URLSearchParams()
  params.set('encryption', 'none')
  params.set('type', input.transport || 'tcp')
  if (input.flow) params.set('flow', input.flow)
  if (input.wsPath) params.set('path', input.wsPath)
  if (input.tlsEnabled) {
    params.set('security', 'tls')
    if (input.sni) params.set('sni', input.sni)
    params.set('alpn', 'h2,http/1.1')
  }
  return `vless://${encodeURIComponent(input.uuid)}@${input.host}:${input.port}?${params.toString()}#${encodeURIComponent(input.email)}`
}

function buildTrojan(input: XrayClientUriInput): string {
  const params = new URLSearchParams()
  params.set('type', input.transport || 'tcp')
  return `trojan://${encodeURIComponent(input.uuid)}@${input.host}:${input.port}?${params.toString()}#${encodeURIComponent(input.email)}`
}

/**
 * shadowsocks SIP002: ss://base64url(method:password)@host:port#remark
 * 这里 method 默认 chacha20-ietf-poly1305 (Xray ss inbound 的常见选择), client_uuid 复用为 password。
 * nook 当前 inbound 模板未提供 ss, 该分支留作后续扩展。
 */
function buildShadowsocks(input: XrayClientUriInput): string {
  const userInfo = base64UrlSafe(`chacha20-ietf-poly1305:${input.uuid}`)
  return `ss://${userInfo}@${input.host}:${input.port}#${encodeURIComponent(input.email)}`
}

/** vmess 历史使用普通 base64 + url-safe 替换 +/ → -_; 去掉 padding。 */
function base64UrlSafe(s: string): string {
  // btoa 接受 binary string, 中文等需先转 UTF-8 字节
  const utf8 = unescape(encodeURIComponent(s))
  return btoa(utf8).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
