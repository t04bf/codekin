import { z } from 'zod'
export const emailSchema = z.string().trim().toLowerCase().email('请输入有效的邮箱地址').max(254, '邮箱地址过长')
export const phoneSchema = z.string().trim().regex(/^1[3-9]\d{9}$/, '请输入有效的中国大陆手机号')
export const codeSchema = z.string().trim().regex(/^\d{6}$/, '请输入六位数字验证码')
