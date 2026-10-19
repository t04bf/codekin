import { defineComponent, h, type PropType, type VNodeChild } from 'vue'
import type { RichNode } from '../types'
import { safeResumeLink } from '../rendering'

/** 只渲染受支持的富文本节点，不使用 v-html，也不透传用户事件/样式属性。 */
export default defineComponent({
  name: 'ResumeRichText',
  props: { value: Object as PropType<RichNode> },
  setup(props) {
    function render(node: RichNode, depth = 0): VNodeChild {
      if (depth > 30) return null
      if (node.type === 'text') {
        let text: VNodeChild = node.text ?? ''
        for (const mark of node.marks ?? []) {
          const tag = { bold: 'strong', italic: 'em', underline: 'u', strike: 's', code: 'code' }[mark.type]
          if (tag) text = h(tag, {}, [text])
          else if (mark.type === 'link') {
            const href = safeResumeLink(mark.attrs?.href)
            if (href) text = h('a', { href, target: '_blank', rel: 'noopener noreferrer' }, [text])
          } else if (['textStyle', 'highlight'].includes(mark.type) && /^#[\da-f]{3,8}$/i.test(mark.attrs?.color ?? '')) {
            text = h('span', { style: { [mark.type === 'highlight' ? 'backgroundColor' : 'color']: mark.attrs?.color } }, [text])
          }
        }
        return text
      }
      if (node.type === 'hardBreak') return h('br')
      const tags: Record<string, string> = { doc: 'div', paragraph: 'p', bulletList: 'ul', orderedList: 'ol', listItem: 'li', blockquote: 'blockquote', codeBlock: 'pre' }
      const tag = node.type === 'heading' ? `h${Math.min(6, Math.max(2, node.attrs?.level ?? 3))}` : tags[node.type]
      if (!tag) return null
      const align = node.attrs?.textAlign
      return h(tag, { style: align && ['left', 'center', 'right', 'justify'].includes(align) ? { textAlign: align } : undefined }, (node.content ?? []).map(child => render(child, depth + 1)))
    }
    return () => h('div', { class: 'resume-rich-text' }, props.value ? [render(props.value)] : [])
  },
})
