import axios, { type AxiosResponse } from 'axios'

/** 后端以 HTTP 200 返回业务结果，必须检查 code。 */
export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T | null
}

export class ApiBusinessError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiBusinessError'
    this.code = code
  }
}

export const publicHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
  timeout: 15_000,
})

export function readData<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  const body = response.data
  if (!body || body.code !== '0') {
    throw new ApiBusinessError(body?.code || 'INVALID_RESPONSE', body?.message || '服务暂时不可用')
  }
  return body.data as T
}
