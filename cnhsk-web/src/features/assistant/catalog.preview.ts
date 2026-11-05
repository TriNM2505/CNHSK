import { createElement, useState } from 'react'

import type { PreviewEntry } from '@/app/preview/types'

import { AssistantPanel } from './components/AssistantPanel'
import type { ChatTurn } from './types'

const answers = {
  system: 'Bạn có thể mở mục Chủ đề để chọn nội dung muốn học.',
  outside:
    'Mình chỉ hỗ trợ cách dùng CNHSK. Để tra nghĩa từ hãy dùng Tra từ điển, để dịch câu hãy dùng Dịch.',
}

function PanelPreview({ outside }: { outside: boolean }) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<ChatTurn[]>([])
  return createElement(AssistantPanel, {
    draft,
    messages,
    pending: false,
    onDraftChange: setDraft,
    onSubmit: () => {
      if (!draft.trim()) return
      setMessages((current) => [
        ...current,
        { role: 'user', text: draft.trim() },
        { role: 'assistant', text: outside ? answers.outside : answers.system },
      ])
      setDraft('')
    },
    onCollapse: () => undefined,
    onNewConversation: () => {
      setMessages([])
      setDraft('')
    },
  })
}

export const previews = [
  {
    group: 'component',
    id: 'assistant-system',
    title: 'Trợ lý — câu hỏi về hệ thống',
    render: () => createElement(PanelPreview, { outside: false }),
    states: ['ready'],
  },
  {
    group: 'component',
    id: 'assistant-outside',
    title: 'Trợ lý — câu hỏi ngoài phạm vi',
    render: () => createElement(PanelPreview, { outside: true }),
    states: ['ready'],
  },
] satisfies readonly PreviewEntry[]
