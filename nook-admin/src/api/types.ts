/** 后端统一返回结构 (com.nook.common.web.response.Result)。 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}
