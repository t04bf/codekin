import { StrictMode } from 'react'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { CaptchaDialog } from './CaptchaDialog'
import type { CaptchaSdkInstance } from '@/utils/captcha-sdk'
vi.mock('@/utils/captcha-sdk', () => ({ loadCaptchaSdk: vi.fn().mockResolvedValue(undefined) }))
let instances: FakeSdk[] = []
const original = window.TAC
class FakeSdk implements CaptchaSdkInstance {
  config = { doSendRequest: vi.fn(), addRequestChain: vi.fn() }
  options: Record<string, unknown>
  init = vi.fn(() => this)
  reloadCaptcha = vi.fn()
  destroyWindow = vi.fn()
  constructor(options: Record<string, unknown>) {
    expect(document.querySelector(String(options.bindEl))).not.toBeNull()
    this.options = options
    instances.push(this)
  }
}
beforeEach(() => { instances = []; window.TAC = FakeSdk })
afterEach(() => { window.TAC = original })
it('mounts the SDK into a real dialog container under StrictMode and ignores callbacks from a closed dialog', async () => {
  const verified = vi.fn()
  const change = vi.fn()
  const result = render(<StrictMode><CaptchaDialog open onOpenChange={change} onVerified={verified}/></StrictMode>)
  await waitFor(() => expect(instances.length).toBe(1))
  expect(instances[0].init).toHaveBeenCalledTimes(1)
  const success = instances[0].options.validSuccess as (response: { data: { captchaId: string } }) => void
  success({ data: { captchaId: 'verified-test' } })
  expect(verified).toHaveBeenCalledWith('verified-test')
  result.rerender(<StrictMode><CaptchaDialog open={false} onOpenChange={change} onVerified={verified}/></StrictMode>)
  expect(instances[0].destroyWindow).toHaveBeenCalledTimes(1)
  success({ data: { captchaId: 'stale-test' } })
  expect(verified).toHaveBeenCalledTimes(1)
})
