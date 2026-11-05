import { useEffect, useRef } from 'react'

import { Button } from '@/shared/ui'

import type { ChatTurn } from '../types'

type AssistantPanelProps = {
  draft: string
  messages: readonly ChatTurn[]
  pending: boolean
  error?: string
  onDraftChange: (value: string) => void
  onSubmit: () => void
  onCollapse: () => void
  onNewConversation: () => void
}

export function AssistantPanel({
  draft,
  messages,
  pending,
  error,
  onDraftChange,
  onSubmit,
  onCollapse,
  onNewConversation,
}: AssistantPanelProps) {
  const length = Array.from(draft).length
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  return (
    <section
      aria-label="Trợ lý hỗ trợ CNHSK"
      className="flex max-h-[calc(100dvh-9rem)] flex-col gap-4 rounded-md border border-line bg-surface p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-h3">Trợ lý hỗ trợ CNHSK</h2>
        <Button size="sm" variant="outline" onClick={onCollapse}>
          Thu gọn
        </Button>
      </div>
      <Button size="sm" variant="outline" onClick={onNewConversation}>
        Cuộc trò chuyện mới
      </Button>
      <div
        aria-label="Tin nhắn"
        role="log"
        aria-live="polite"
        className="min-h-0 flex-1 space-y-3 overflow-y-auto"
      >
        {messages.map((message, index) => (
          <div key={index} className="whitespace-pre-wrap break-words text-body-sm">
            <p className="font-bold">{message.role === 'user' ? 'Bạn' : 'Trợ lý'}</p>
            <p>{message.text}</p>
          </div>
        ))}
      </div>
      {pending && <p role="status">Đang gửi câu hỏi…</p>}
      {error && <p role="alert">{error}</p>}
      <form
        className="space-y-2"
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
        <label className="block text-body-sm font-bold" htmlFor="assistant-question">
          Câu hỏi về cách dùng CNHSK
        </label>
        <textarea
          ref={inputRef}
          id="assistant-question"
          aria-describedby="assistant-question-count"
          className="min-h-24 w-full resize-y rounded-sm border border-line bg-surface p-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          value={draft}
          onChange={(event) => onDraftChange(event.target.value)}
        />
        <p id="assistant-question-count" className="text-caption text-ink-secondary">
          {length}/2.000 ký tự
        </p>
        <Button type="submit" disabled={pending || !draft.trim() || length > 2000}>
          Gửi
        </Button>
      </form>
    </section>
  )
}
