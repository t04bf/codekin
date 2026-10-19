import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Field from '@/components/editor/Field'
import BasicPanel from '@/components/editor/basic/BasicPanel'
import { ResumeEditorProvider, useResumeStore } from '@/store/useResumeStore'
import { NextIntlClientProvider } from '@/i18n/compat/client'
import messages from '@/i18n/locales/zh.json'
import { useAuthStore } from '@/features/auth/session'
import { fileApi } from '@/api/file'
import * as images from '@/utils/imageUtils'

beforeEach(() => { useAuthStore.getState().clear() })
afterEach(() => vi.restoreAllMocks())

describe('resume editor integration', () => {
  it('edits birthday year/month segments and restores the saved value', async () => {
    function Birthday() {
      const [value, setValue] = useState('1995-01')
      return <><Field label="生日" type="date" value={value} onChange={setValue}/><output data-testid="birthday">{value}</output><button onClick={() => setValue('1995-01')}>恢复生日</button></>
    }
    render(<Birthday/>)
    const user = userEvent.setup()
    const [year, month] = screen.getAllByRole('spinbutton')
    expect(year).toHaveAttribute('aria-valuenow', '1995')
    expect(month).toHaveAttribute('aria-valuenow', '1')
    await user.click(year)
    await user.keyboard('1996')
    await user.click(month)
    await user.keyboard('08')
    expect(screen.getByTestId('birthday')).toHaveTextContent('1996/08')
    await user.keyboard('{ArrowUp}')
    expect(screen.getByTestId('birthday')).toHaveTextContent('1996/09')
    await user.click(screen.getByRole('button', { name: '恢复生日' }))
    expect(year).toHaveAttribute('aria-valuenow', '1995')
    expect(month).toHaveAttribute('aria-valuenow', '1')
  })

  it('keeps either segmented month independently editable, including end-first and clearing start', async () => {
    function Dates() {
      const [value, setValue] = useState('')
      return <><Field label="任职时间" type="date-range" value={value} onChange={setValue} showPresentSwitch/><output data-testid="range">{value}</output></>
    }
    render(<Dates/>)
    const user = userEvent.setup()
    const [startYear, startMonth, endYear, endMonth] = screen.getAllByRole('spinbutton')
    await user.click(endYear)
    await user.keyboard('2026')
    await user.click(endMonth)
    await user.keyboard('10')
    expect(screen.getByTestId('range')).toHaveTextContent('- 2026/10')
    await user.click(startYear)
    await user.keyboard('2023')
    await user.click(startMonth)
    await user.keyboard('01')
    expect(screen.getByTestId('range')).toHaveTextContent('2023/01 - 2026/10')
    await user.click(startYear)
    await user.keyboard('{Backspace}')
    expect(endYear).toHaveAttribute('aria-valuenow', '2026')
    expect(endMonth).toHaveAttribute('aria-valuenow', '10')
    expect(screen.getByTestId('range')).toHaveTextContent('- 2026/10')
    await user.click(screen.getByRole('switch', { name: '至今' }))
    expect(endYear).toHaveAttribute('aria-disabled', 'true')
    expect(endMonth).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByTestId('range')).toHaveTextContent('- 至今')
    await user.click(startYear)
    await user.keyboard('2024')
    await user.click(startMonth)
    await user.keyboard('03')
    expect(screen.getByTestId('range')).toHaveTextContent('2024/03 - 至今')
    await user.click(screen.getByRole('switch', { name: '至今' }))
    expect(endYear).not.toHaveAttribute('aria-disabled', 'true')
    await user.click(endYear)
    await user.keyboard('2026')
    await user.click(endMonth)
    await user.keyboard('10')
    expect(screen.getByTestId('range')).toHaveTextContent('2024/03 - 2026/10')
  })

  it('validates ranges across saved date separators and restores both segments after external updates', async () => {
    function Dates() {
      const [value, setValue] = useState('2024-03 - 2026.10')
      return <><Field label="项目时间" type="date-range" value={value} onChange={setValue}/>
        <button onClick={() => setValue('2024-03 - 2026.10')}>恢复时间</button></>
    }
    render(<Dates/>)
    const user = userEvent.setup()
    const [startYear, startMonth, endYear, endMonth] = screen.getAllByRole('spinbutton')
    expect(startYear).toHaveAttribute('aria-valuenow', '2024')
    expect(startMonth).toHaveAttribute('aria-valuenow', '3')
    expect(endYear).toHaveAttribute('aria-valuenow', '2026')
    expect(endMonth).toHaveAttribute('aria-valuenow', '10')
    await user.click(endYear)
    await user.keyboard('2023')
    expect(endMonth).toHaveAttribute('aria-valuenow', '10')
    expect(screen.getByText('结束时间不能早于开始时间')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '恢复时间' }))
    expect(endYear).toHaveAttribute('aria-valuenow', '2026')
    expect(endMonth).toHaveAttribute('aria-valuenow', '10')
    expect(screen.queryByText('结束时间不能早于开始时间')).not.toBeInTheDocument()
  })

  it('restores basic and custom fields in the mounted form on undo/redo', async () => {
    function Editor() {
      const state = useResumeStore()
      return state.activeResume ? <><button onClick={state.undo}>撤销测试</button><button onClick={state.redo}>重做测试</button><BasicPanel/></> : <button onClick={() => state.createResume('classic')}>创建测试草稿</button>
    }
    render(<QueryClientProvider client={new QueryClient()}><NextIntlClientProvider locale="zh" messages={messages}><ResumeEditorProvider><Editor/></ResumeEditorProvider></NextIntlClientProvider></QueryClientProvider>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '创建测试草稿' }))
    await user.click(screen.getByRole('button', { name: '添加自定义字段' }))
    expect(screen.getAllByRole('textbox', { name: /^标签$/ })).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: '撤销测试' }))
    expect(screen.getAllByRole('textbox', { name: /^标签$/ })).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: '重做测试' }))
    expect(screen.getAllByRole('textbox', { name: /^标签$/ })).toHaveLength(2)
  })

  it('clears in-memory edits after remount or switching accounts', async () => {
    function Probe() {
      const state = useResumeStore()
      return <><output data-testid="name">{state.activeResume?.basic.name || 'Empty'}</output><button onClick={() => {
        state.createResume('classic'); state.updateBasicInfo({ name: 'Unsaved' })
      }}>Create</button></>
    }
    const first = render(<ResumeEditorProvider><Probe/></ResumeEditorProvider>)
    fireEvent.click(await screen.findByRole('button', { name: 'Create' }))
    expect(screen.getByTestId('name')).toHaveTextContent('Unsaved')
    first.unmount()
    render(<ResumeEditorProvider><Probe/></ResumeEditorProvider>)
    expect(await screen.findByTestId('name')).toHaveTextContent('Empty')
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(screen.getByTestId('name')).toHaveTextContent('Unsaved')
    act(() => useAuthStore.setState({ userId: '43' }))
    expect(screen.getByTestId('name')).toHaveTextContent('Empty')
  })

  it('keeps the previous portrait after failed direct upload and commits only after successful confirmation', async () => {
    useAuthStore.setState({ userId: '42' })
    vi.spyOn(fileApi, 'uploadTypes').mockResolvedValue([{ code: 'RESUME_PHOTO', extensions: ['png'], contentTypes: ['image/png'], maxBytes: 100, maxWidth: 100, maxHeight: 100, maxPixels: 10000 }])
    vi.spyOn(images, 'compressImage').mockResolvedValue('data:image/png;base64,new-photo')
    vi.spyOn(fileApi, 'signature').mockResolvedValue({ fileId: 'portrait-id', method: 'POST', uploadUrl: 'https://storage.example/upload', fields: {}, expiresAt: '', confirmationExpiresAt: '' })
    const upload = vi.spyOn(fileApi, 'directUpload').mockRejectedValueOnce(new Error('上传失败')).mockResolvedValue(undefined)
    const complete = vi.spyOn(fileApi, 'complete').mockResolvedValue({ fileId: 'portrait-id', typeCode: 'RESUME_PHOTO', contentType: 'image/png', size: 3 })
    function Editor() {
      const state = useResumeStore()
      return state.activeResume ? <><output data-testid="photo">{state.activeResume.basic.photo}</output><BasicPanel/></> : <button onClick={() => state.createResume('classic')}>创建上传草稿</button>
    }
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><NextIntlClientProvider locale="zh" messages={messages}><ResumeEditorProvider><Editor/></ResumeEditorProvider></NextIntlClientProvider></QueryClientProvider>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '创建上传草稿' }))
    const previous = screen.getByTestId('photo').textContent
    await user.click(screen.getByRole('button', { name: '设置简历头像' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '上传本地图片' })).toBeEnabled())
    const image = new File(['png'], 'portrait.png', { type: 'image/png' })
    // jsdom 不实现 Vaul 手势所需的 Pointer Capture；文件选择手势由浏览器验证覆盖。
    fireEvent.change(screen.getByLabelText('简历证件照文件'), { target: { files: [image] } })
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(screen.getByRole('button', { name: '上传本地图片' })).toBeEnabled())
    expect(complete).not.toHaveBeenCalled()
    expect(screen.getByTestId('photo').textContent).toBe(previous)
    fireEvent.change(screen.getByLabelText('简历证件照文件'), { target: { files: [image] } })
    await waitFor(() => expect(screen.getByTestId('photo')).toHaveTextContent('data:image/png;base64,new-photo'))
    expect(complete).toHaveBeenCalledWith('portrait-id')
    expect(screen.getByLabelText('简历照片链接')).toHaveValue('')
    fireEvent.focus(screen.getByLabelText('简历照片链接'))
    fireEvent.blur(screen.getByLabelText('简历照片链接'))
    expect(screen.getByTestId('photo')).toHaveTextContent('data:image/png;base64,new-photo')
  })
})
