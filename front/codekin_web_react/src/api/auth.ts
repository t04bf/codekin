import { publicHttp, readData, type ApiEnvelope } from './http'

export interface LoginTokens {
  userId: string
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresAt: string
  refreshExpiresAt: string
  sessionExpiresAt: string
}

export const authApi = {
  passwordLogin(identifier: string, password: string) {
    return publicHttp.post<ApiEnvelope<LoginTokens>>('/api/auth/password/login', {
      identifier,
      password,
    }).then(readData)
  },
  sendEmailCode(email: string, captchaId: string) {
    return publicHttp.post<ApiEnvelope<void>>('/api/auth/email/login/send', { email, captchaId }).then(readData)
  },
  emailLogin(email: string, code: string) {
    return publicHttp.post<ApiEnvelope<LoginTokens>>('/api/auth/email/login', { email, code }).then(readData)
  },
  sendSmsCode(phone: string, captchaId: string) {
    return publicHttp.post<ApiEnvelope<void>>('/api/auth/sms/login/send', { phone, captchaId }).then(readData)
  },
  smsLogin(phone: string, code: string) {
    return publicHttp.post<ApiEnvelope<LoginTokens>>('/api/auth/sms/login', { phone, code }).then(readData)
  },
}
