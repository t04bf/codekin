/** 联系方式仅在输入新绑定目标时展示完整值，账号信息统一脱敏。 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '未绑定'
  const number = phone.replace(/^\+86/, '')
  return /^1\d{10}$/.test(number) ? number.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '已绑定'
}

/** 保留邮箱前缀的一部分和域名，短前缀也不会完整暴露。 */
export function maskEmail(email: string | null | undefined): string {
  if (!email) return '未绑定'
  const [name, domain] = email.split('@')
  if (!name || !domain) return '已绑定'
  return `${name.slice(0, name.length > 3 ? 3 : 1)}****@${domain}`
}
