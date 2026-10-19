import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResumeEditorContext } from '@/store/resumeEditorContext'
import { createResumeEditorStore } from '@/store/resumeEditorStore'
import { A4_HEIGHT_PX } from '@/lib/resumePaper'
import { ResumePreview } from './ResumePreview'

vi.mock('@/components/templates', () => ({ default: () => <div>简历正文</div> }))
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

function setup(height: number, enabled = false) {
  const editor = createResumeEditorStore()
  editor.store.getState().createResume('classic')
  editor.store.getState().updateGlobalSettings({ pagePadding: 12, autoOnePage: enabled, pageBreakLinesVisible: true })
  vi.stubGlobal('ResizeObserver', class {
    constructor(private callback: (entries: unknown[]) => void) {}
    observe(target: HTMLElement) {
      this.callback([{ target, contentRect: { width: 850 }, borderBoxSize: [{ blockSize: height }] }])
    }
    disconnect() {}
  })
  const view = render(<ResumeEditorContext.Provider value={editor}><ResumePreview/></ResumeEditorContext.Provider>)
  const content = view.container.querySelector('#resume-preview')!.firstElementChild as HTMLElement
  return { editor, content }
}

describe('one-page preview', () => {
  it('fits overflowing content, leaves viewing zoom independent, and supports switching off and undo', async () => {
    const { editor, content } = setup(1228.25)
    const user = userEvent.setup()
    expect(screen.getByText('第 1 页结束')).toBeInTheDocument()
    const toggle = screen.getByRole('switch', { name: '一页纸' })
    await user.click(toggle)
    const ratio = (A4_HEIGHT_PX - 24) / 1204.25
    expect(content.style.transform).toBe(`scale(${ratio})`)
    expect(toggle).toBeChecked()
    expect(screen.queryByText('第 1 页结束')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '放大预览' }))
    expect(content.style.transform).toBe(`scale(${ratio})`)
    // 连续设置在一秒内会合并撤销历史，模拟一次独立的后续操作。
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 1001)
    await user.click(toggle)
    expect(content.style.transform).toBe('scale(1)')
    expect(screen.getByText('第 1 页结束')).toBeInTheDocument()
    act(() => editor.store.getState().undo())
    expect(toggle).toBeChecked()
    expect(screen.queryByText('第 1 页结束')).not.toBeInTheDocument()
  })

  it('does not shrink a resume that already fits exactly on A4', () => {
    const { content } = setup(A4_HEIGHT_PX, true)
    expect(content.style.transform).toBe('scale(1)')
    expect(screen.queryByText('第 1 页结束')).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('retains pagination and warns when 90% scaling still cannot fit', () => {
    const { content } = setup(1400, true)
    expect(content.style.transform).toBe('scale(0.9)')
    expect(screen.getByText('第 1 页结束')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('缩小后仍超过一页')
  })
})
