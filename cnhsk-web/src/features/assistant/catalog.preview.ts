import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'

import { AssistantPanel, type AssistantMessage } from './components/AssistantPanel'

const answers = {
  system: 'Bạn có thể mở mục Chủ đề để chọn nội dung muốn học.',
  outside:
    'Mình chỉ hỗ trợ cách dùng CNHSK. Để tra nghĩa từ hãy dùng Tra từ điển, để dịch câu hãy dùng Dịch.',
}

function PanelPreview({
  outside,
  state = 'ready',
  errorMessage,
}: {
  outside: boolean
  state?: PreviewState
  errorMessage?: string
}) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<AssistantMessage[]>([])
  const [pending, setPending] = useState(state === 'loading')
  const [error, setError] = useState(errorMessage)
  return createElement(AssistantPanel, {
    draft,
    messages,
    pending,
    error,
    onDraftChange: setDraft,
    onSubmit: () => {
      if (!draft.trim() || pending) return
      setMessages((current) => [
        ...current,
        { role: 'user', text: draft.trim() },
        {
          role: 'assistant',
          text: outside ? answers.outside : answers.system,
          outOfScope: outside,
        },
      ])
      setDraft('')
      setError(undefined)
    },
    onCollapse: () => undefined,
    onNewConversation: () => {
      setMessages([])
      setDraft('')
      setPending(false)
      setError(undefined)
    },
  })
}

export const previews = [
  {
    group: 'component',
    id: 'assistant-system',
    title: 'Trợ lý — câu hỏi về hệ thống',
    render: (state) => createElement(PanelPreview, { outside: false, state }),
    states: ['ready', 'loading'],
  },
  {
    group: 'component',
    id: 'assistant-outside',
    title: 'Trợ lý — câu hỏi ngoài phạm vi',
    render: () => createElement(PanelPreview, { outside: true }),
    states: ['ready'],
  },
  ...[
    {
      id: 'assistant-rate-limited',
      title: 'Trợ lý — giới hạn trong ngày',
      message: 'Bạn đã hỏi nhiều trong hôm nay, quay lại ngày mai',
    },
    {
      id: 'assistant-blocked',
      title: 'Trợ lý — đang làm bài kiểm tra',
      message: 'Trợ lý tắt trong lúc làm bài',
    },
    {
      id: 'assistant-unavailable',
      title: 'Trợ lý — tạm thời chưa trả lời được',
      message: 'Trợ lý tạm thời chưa trả lời được, bạn thử lại sau',
    },
  ].map(({ id, title, message }) => ({
    group: 'component' as const,
    id,
    title,
    render: () => createElement(PanelPreview, { outside: false, errorMessage: message }),
    states: ['ready'] as const,
  })),
] satisfies readonly PreviewEntry[]
