import request from '@/api/request'
import { streamPost } from '@/api/stream'
import type { XrayLog } from '@/api/resource/server-ops'

/** Xray 实例元数据 (装机契约 / 部署事实); 跟 resource_server 1:1 */
export interface XrayInstall {
  serverId: string
  /** 服务器别名 (后端 enrich) */
  serverName?: string
  /** 服务器主机 (后端 enrich) */
  serverHost?: string
  xrayVersion?: string
  xrayApiPort?: number
  xrayInstallDir?: string
  /** xray binary 绝对路径; 装机时落库 */
  xrayBinaryPath?: string
  /** xray config.json 绝对路径; 装机时落库 */
  xrayConfigPath?: string
  /** xray share 目录 (geo*.dat); 装机时落库 */
  xrayShareDir?: string
  xrayLogDir?: string
  /** 全节点固定常量, 后端回填 (/etc/systemd/system/xray.service) */
  xraySystemdUnitPath?: string
  /** 绑定的根域 system_domain.id; 空 = 未绑 / 不用 TLS */
  domainId?: string
  /** 二级域名标签 (如 frontline-jp-1) */
  subdomain?: string
  /** 完整域名 FQDN (subdomain + 根域; 后端回填) */
  domain?: string
  /** 最近一次部署完成时间 (重装覆写) */
  installedAt?: string
  /** 上次探测到的 xray 启动时间; 重装清零 */
  lastXrayUptime?: string
  createdAt?: string
  updatedAt?: string
}

/** 按 serverId 取 xray 实例元数据 (server detail tab 用); 不存在抛 SERVER_STATE_NOT_FOUND */
export function getXrayInstall(serverId: string) {
  return request.get<unknown, XrayInstall>('/admin/xray/install/get-xray-install', { params: { serverId } })
}

/** xray 文件日志变体: access (每个连接) vs error (xray 内部错误). */
export type XrayLogFileVariant = 'access' | 'error'

/**
 * 拉 xray 自己的日志文件 (access.log / error.log); 跟 journalctl 互补.
 * file 看真正的连接 / 错误日志, journal 只有 systemd 启动 banner.
 */
export function getXrayLogFile(
  serverId: string,
  opts?: { variant?: XrayLogFileVariant; lines?: number; keyword?: string }
) {
  return request.get<unknown, XrayLog>('/admin/xray/install/get-xray-log-file', {
    params: {
      id: serverId,
      variant: opts?.variant,
      lines: opts?.lines,
      keyword: opts?.keyword?.trim() || undefined
    }
  })
}

/** 重启 Xray 服务; 客户连接会断 1-2 秒. */
export function xrayRestart(serverId: string) {
  return request.post<unknown, string>('/admin/xray/install/restart-xray', null, { params: { id: serverId } })
}

/** 开/关 Xray 开机自启 (systemctl enable/disable); 末尾返回 is-enabled 结果给前端确认. */
export function xrayAutostart(serverId: string, enabled: boolean) {
  return request.post<unknown, string>('/admin/xray/install/set-xray-autostart', null, { params: { id: serverId, enabled } })
}

/** vmess + ws 协议特定参数. */
export interface VmessWsInput {
  /** WebSocket 接入路径 (/ 开头). */
  wsPath: string
  /** 绑定根域 system_domain.id; 非空走 TLS, 空走纯 ws. */
  domainId?: string
  /** 二级域名标签; 绑域名时必填. */
  subdomain?: string
}

/** vless + reality 协议特定参数. */
export interface VlessRealityInput {
  /** REALITY 偷取目标主机名 (如 www.bing.com). */
  realityDest: string
}

/**
 * 共享 inbound 配置 (协议形态键 + 监听 + 协议特定参数 params).
 * 协议特定字段收进 params, 由后端按 protocol 多态绑定 (vmess→VmessWsInput / vless→VlessRealityInput).
 */
export interface XrayInboundConfig {
  /** 协议; vmess (走 ws) 或 vless (走 reality); 同时是 params 的多态判别键. */
  protocol: 'vmess' | 'vless' | 'trojan'
  /** 监听端口 (默认 443). */
  sharedInboundPort: number
  /** 协议特定入站参数; 按 protocol 决定形状. */
  params: VmessWsInput | VlessRealityInput
}

/**
 * Xray 线路服务器一键安装入参.
 *
 * <p>仅装 xray 内核 + 落地池 + UFW + 时区; swap / bbr 等通用 OS 调优走 ServerOps 接口单独触发.
 *
 * <p>基础设施 (安装目录 / 各路径 / api 端口 / 日志目录 / 日志级别 / 重启策略 / TLS 路径) 由后端 XrayInstallDefaults 固定;
 * 前端传版本 + 行为开关 + inbound 配置.
 */
export interface LineServerInstallDTO {
  /** Xray 版本; "v26.3.27" 这种或 "latest". */
  xrayVersion: string
  /** 是否 systemctl enable xray (机器重启后自动起 xray). */
  enableOnBoot: boolean
  /** 强制重装; 即使版本号一致也走下载流程, 用于自编译 / build 后缀差异. */
  forceReinstall: boolean
  installUfw: boolean
  /** true = 设置远端时区为 Asia/Shanghai, false = 跳过 (10-timezone 模块不渲染). */
  setTimezone: boolean
  /** true = 装 logrotate 自动滚 xray 日志 (低配机推荐); false = 跳过 (日志可能填满磁盘). */
  logRotate: boolean
  /** 共享 inbound 配置. */
  inbound: XrayInboundConfig
}

/** REALITY dest 候选 (装机 vless 协议时下拉). */
export interface RealityDest {
  value: string
  label: string
}

/** 列 REALITY dest 候选. */
export function listRealityDest() {
  return request.get<unknown, RealityDest[]>('/admin/xray/install/list-reality-dest')
}

/** 协议装机表单单字段描述 (后端 InboundFieldSchema; 前端动态渲染 + 校验). */
export interface InboundFieldSchema {
  name: string
  label: string
  type: 'text' | 'select' | 'number'
  required: boolean
  /** 此字段非空时本字段才必填 (如 subdomain ← domainId). */
  requiredWhenField?: string
  defaultValue?: unknown
  placeholder?: string
  /** text 校验正则. */
  pattern?: string
  /** select 候选来源 key (前端 loader 注册表解析: domains / realityDest). */
  optionsKey?: string
  /** select 是否允许自定义输入. */
  allowCustom?: boolean
}

/** 单协议装机表单 schema (后端 ProtocolSchemaRespVO). */
export interface ProtocolSchema {
  protocol: string
  label: string
  fields: InboundFieldSchema[]
}

/** 列出全部协议的装机表单 schema (动态渲染协议下拉 + 字段). */
export function getProtocolSchemas() {
  return request.get<unknown, ProtocolSchema[]>('/admin/xray/install/list-protocols')
}

/** 一键安装/重装 Xray (流式). */
export function xrayInstallStream(
  serverId: string,
  dto: LineServerInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  return streamPost(`/api/admin/xray/install/install-xray?id=${encodeURIComponent(serverId)}`, dto, onChunk, signal)
}
