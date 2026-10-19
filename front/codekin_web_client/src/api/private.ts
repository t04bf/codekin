import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ApiBusinessError, readData, type ApiEnvelope } from './http'

export const privateHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
  timeout: 15_000,
})

privateHttp.interceptors.request.use(async (config) => {
  const auth = useAuthStore()
  if (!await auth.ensureAccessToken()) {
    throw new ApiBusinessError('AUTH_REQUIRED', '登录状态已失效，请重新登录')
  }
  config.headers.Authorization = `Bearer ${auth.accessToken}`
  return config
})

export interface MyUser {
  userId: string
  nickname: string
  avatarKey: string | null
  avatarUrl: string | null
  bio: string | null
  genderCode: string | null
  birthDate: string | null
  realName: string | null
  email: string | null
  phone: string | null
  hasPassword: boolean
  lastLoginIp: string | null
  lastLoginAt: string | null
}

/** 替换可编辑资料，NULL 明确清空选填字段；身份由登录态决定。 */
export interface ProfileUpdate {
  nickname: string
  bio: string | null
  genderCode: 'MALE' | 'FEMALE' | 'OTHER' | null
  birthDate: string | null
  realName: string | null
}

/** 对象路径用于保存；短期签名 URL 仅用于展示。 */
export interface DefaultAvatar {
  name: string
  objectKey: string
  url: string
}

export const userApi = {
  async getMe() {
    const profile = await privateHttp.get<ApiEnvelope<MyUser>>('/api/users/me').then(readData)
    // 密码状态为必填布尔值，字段缺失不能当成“未设置密码”。
    if (!profile || typeof profile.hasPassword !== 'boolean') {
      throw new ApiBusinessError('INVALID_USER_PROFILE', '个人资料加载失败，请重试')
    }
    return profile
  },
  updateProfile(profile: ProfileUpdate) {
    return privateHttp.put<ApiEnvelope<void>>('/api/users/me/profile', profile).then(readData)
  },
  setAvatar(fileId: string) {
    return privateHttp.put<ApiEnvelope<void>>('/api/users/me/avatar', { fileId }).then(readData)
  },
  getDefaultAvatars() {
    return privateHttp.get<ApiEnvelope<DefaultAvatar[]>>('/api/users/avatar/defaults').then(readData)
  },
  setDefaultAvatar(objectKey: string) {
    return privateHttp.put<ApiEnvelope<void>>('/api/users/me/avatar/default', { objectKey }).then(readData)
  },
  sendBindPhoneCode(phone: string) {
    return privateHttp.post<ApiEnvelope<void>>('/api/users/me/phone/sms/send', { phone }).then(readData)
  },
  bindPhone(phone: string, code: string) {
    return privateHttp.post<ApiEnvelope<void>>('/api/users/me/phone/bind', { phone, code }).then(readData)
  },
  sendBindEmailCode(email: string) {
    return privateHttp.post<ApiEnvelope<void>>('/api/users/me/email/code/send', { email }).then(readData)
  },
  bindEmail(email: string, code: string) {
    return privateHttp.post<ApiEnvelope<void>>('/api/users/me/email/bind', { email, code }).then(readData)
  },
  setPassword(newPassword: string, oldPassword?: string) {
    return privateHttp.put<ApiEnvelope<void>>('/api/users/me/password', { newPassword, oldPassword }).then(readData)
  },
  logout() {
    return privateHttp.post<ApiEnvelope<void>>('/api/auth/logout').then(readData)
  },
}
