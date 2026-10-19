import axios from 'axios'
export function errorMessage(error: unknown, fallback = '请求失败，请稍后重试') {
  return axios.isAxiosError(error) ? '网络连接失败，请稍后重试' : error instanceof Error ? error.message : fallback
}
