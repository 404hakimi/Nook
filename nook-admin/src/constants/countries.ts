/** 国家/地区预设表: 选国家即自动带出 countryCode / countryName / 国旗. */
export interface CountryOption {
  /** ISO 3166-1 alpha-2 (大写); flag-icons 用它渲染国旗. */
  code: string
  /** 中文名. */
  name: string
}

/** ISO alpha-2 → 国旗 emoji (区域指示符拼接); 例: 'JP' → 🇯🇵. */
export function countryFlagEmoji(code?: string | null): string {
  if (!code || code.length !== 2) return ''
  const cc = code.toUpperCase()
  if (!/^[A-Z]{2}$/.test(cc)) return ''
  const base = 0x1f1e6 // 'A' 的区域指示符
  return String.fromCodePoint(base + (cc.charCodeAt(0) - 65), base + (cc.charCodeAt(1) - 65))
}

/** 常用国家/地区 (代理业务侧重亚太 + 欧美); 不求全, 缺的可在区域管理手动补 countryCode. */
export const COUNTRIES: CountryOption[] = [
  { code: 'HK', name: '香港' },
  { code: 'TW', name: '台湾' },
  { code: 'JP', name: '日本' },
  { code: 'KR', name: '韩国' },
  { code: 'SG', name: '新加坡' },
  { code: 'US', name: '美国' },
  { code: 'CA', name: '加拿大' },
  { code: 'GB', name: '英国' },
  { code: 'DE', name: '德国' },
  { code: 'FR', name: '法国' },
  { code: 'NL', name: '荷兰' },
  { code: 'RU', name: '俄罗斯' },
  { code: 'AU', name: '澳大利亚' },
  { code: 'NZ', name: '新西兰' },
  { code: 'IN', name: '印度' },
  { code: 'VN', name: '越南' },
  { code: 'TH', name: '泰国' },
  { code: 'MY', name: '马来西亚' },
  { code: 'ID', name: '印度尼西亚' },
  { code: 'PH', name: '菲律宾' },
  { code: 'KH', name: '柬埔寨' },
  { code: 'MM', name: '缅甸' },
  { code: 'LA', name: '老挝' },
  { code: 'BD', name: '孟加拉国' },
  { code: 'PK', name: '巴基斯坦' },
  { code: 'KZ', name: '哈萨克斯坦' },
  { code: 'TR', name: '土耳其' },
  { code: 'AE', name: '阿联酋' },
  { code: 'SA', name: '沙特阿拉伯' },
  { code: 'IL', name: '以色列' },
  { code: 'IT', name: '意大利' },
  { code: 'ES', name: '西班牙' },
  { code: 'PT', name: '葡萄牙' },
  { code: 'IE', name: '爱尔兰' },
  { code: 'CH', name: '瑞士' },
  { code: 'SE', name: '瑞典' },
  { code: 'NO', name: '挪威' },
  { code: 'FI', name: '芬兰' },
  { code: 'DK', name: '丹麦' },
  { code: 'PL', name: '波兰' },
  { code: 'CZ', name: '捷克' },
  { code: 'AT', name: '奥地利' },
  { code: 'BE', name: '比利时' },
  { code: 'RO', name: '罗马尼亚' },
  { code: 'UA', name: '乌克兰' },
  { code: 'GR', name: '希腊' },
  { code: 'HU', name: '匈牙利' },
  { code: 'BR', name: '巴西' },
  { code: 'MX', name: '墨西哥' },
  { code: 'AR', name: '阿根廷' },
  { code: 'CL', name: '智利' },
  { code: 'CO', name: '哥伦比亚' },
  { code: 'ZA', name: '南非' },
  { code: 'EG', name: '埃及' },
  { code: 'NG', name: '尼日利亚' },
  { code: 'CN', name: '中国' }
]
