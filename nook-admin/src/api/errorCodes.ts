/**
 * 与后端 com.nook.common.web.error.CommonErrorCode 对齐。
 * 业务模块自有错误码（2xxx-7xxx）不在此处枚举，由各 view 按需处理。
 */
export const SUCCESS = 0

export const INTERNAL_ERROR = 1000
export const PARAM_INVALID = 1001
export const UNAUTHORIZED = 1002
export const FORBIDDEN = 1003
export const NOT_FOUND = 1004
export const METHOD_NOT_ALLOWED = 1005
export const RATE_LIMITED = 1006
export const CONCURRENT_CONFLICT = 1007
