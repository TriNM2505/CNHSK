import { ArrowUp, Compass, Crown, GraduationCap, Library, SquarePen, X } from 'lucide-react'
import { useEffect, useRef } from 'react'

import { Button } from '@/shared/ui'

import type { ChatTurn } from '../types'
import { PandaMascot } from './PandaMascot'

export type AssistantMessage = ChatTurn & { outOfScope?: boolean }

type AssistantPanelProps = {
  draft: string
  messages: readonly AssistantMessage[]
  pending: boolean
  error?: string
  onDraftChange: (value: string) => void
  onSubmit: () => void
  onCollapse: () => void
  onNewConversation: () => void
}

const HELP_TOPICS = [
  { icon: Compass, text: 'Tìm đúng màn cho việc bạn muốn làm' },
  { icon: GraduationCap, text: 'Cách làm kiểm tra đầu vào, thi thử, ôn bài' },
  { icon: Crown, text: 'Gói FREE, PREMIUM và cách nạp mã thẻ' },
  { icon: Library, text: 'Dùng Dịch, Bộ thẻ, Sổ tay, Sách HSK' },
] as const

const STARTERS = [
  'Muốn luyện viết thì vào đâu?',
  'Nạp mã PREMIUM thế nào?',
  'Mình nên bắt đầu học từ đâu?',
] as const

const iconButton =
  'flex size-10 items-center justify-center rounded-pill text-ink-secondary hover:bg-brand-50 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

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
  const logRef = useRef<HTMLDivElement>(null)
  const canSend = !pending && draft.trim() !== '' && length <= 2000

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const log = logRef.current
    if (log && typeof log.scrollTo === 'function') log.scrollTo({ top: log.scrollHeight })
  }, [messages.length, pending])

  function pickStarter(text: string) {
    onDraftChange(text)
    inputRef.current?.focus()
  }

  return (
    <section
      aria-label="Trợ lý hỗ trợ CNHSK"
      className="cnhsk-assistant-card flex h-full min-h-0 flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-overlay"
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          event.stopPropagation()
          onCollapse()
        }
      }}
    >
      <header className="flex items-center gap-3 border-b border-line bg-surface-subtle px-4 py-3">
        <span className="relative flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-50">
          <PandaMascot className="size-10" mood={pending ? 'thinking' : 'idle'} />
          <span className="absolute bottom-0.5 right-0.5 size-2.5 rounded-pill bg-success ring-2 ring-surface" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-label text-ink">Trợ lý CNHSK</h2>
          <p className="truncate text-caption text-ink-secondary">
            {pending ? 'Đang soạn câu trả lời…' : 'Hỏi cách dùng hệ thống'}
          </p>
        </div>
        <button
          aria-label="Cuộc trò chuyện mới"
          className={iconButton}
          onClick={onNewConversation}
          title="Cuộc trò chuyện mới"
          type="button"
        >
          <SquarePen aria-hidden="true" className="size-5" />
        </button>
        <button
          aria-label="Thu gọn"
          className={iconButton}
          onClick={onCollapse}
          title="Thu gọn (Esc)"
          type="button"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4" ref={logRef}>
        {messages.length === 0 ? (
          <div className="flex flex-col items-center gap-3 pb-2 text-center">
            <PandaMascot className="size-28" />
            <div className="space-y-1">
              <p className="text-h3 text-ink">Chào bạn, mình là gấu CNHSK</p>
              <p className="text-body-sm text-ink-secondary">
                Hỏi mình cách dùng bất cứ màn nào trên CNHSK nhé!
              </p>
            </div>
            <div className="w-full rounded-md bg-canvas p-4 text-left">
              <p className="text-caption uppercase tracking-wider text-ink-muted">
                Mình có thể giúp bạn
              </p>
              <ul className="mt-2 space-y-2">
                {HELP_TOPICS.map(({ icon: Icon, text }) => (
                  <li className="flex items-start gap-2 text-body-sm text-ink" key={text}>
                    <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-700" />
                    {text}
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex w-full flex-wrap justify-center gap-2">
              {STARTERS.map((text) => (
                <button
                  className="rounded-pill border border-brand-100 bg-surface px-3 py-1.5 text-body-sm text-brand-700 hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  key={text}
                  onClick={() => pickStarter(text)}
                  type="button"
                >
                  {text}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        <div aria-label="Tin nhắn" aria-live="polite" className="space-y-3" role="log">
          {messages.map((message, index) =>
            message.role === 'user' ? (
              <div className="flex justify-end" key={index}>
                <p className="max-w-[85%] whitespace-pre-wrap break-words rounded-lg rounded-br-sm bg-brand-700 px-3.5 py-2 text-body-sm text-surface">
                  <span className="sr-only">Bạn: </span>
                  {message.text}
                </p>
              </div>
            ) : (
              <div className="flex items-end gap-2" key={index}>
                <PandaMascot className="size-7 shrink-0" />
                <div className="max-w-[85%] min-w-0 rounded-lg rounded-bl-sm bg-canvas px-3.5 py-2 text-body-sm text-ink">
                  <p className="whitespace-pre-wrap break-words">
                    <span className="sr-only">Trợ lý: </span>
                    {message.text}
                  </p>
                  {message.outOfScope ? (
                    <div className="mt-2">
                      <Button to="/translate" size="sm" variant="outline">
                        Dịch và tra từ
                      </Button>
                    </div>
                  ) : null}
                </div>
              </div>
            ),
          )}
        </div>

        {pending ? (
          <div className="mt-3 flex items-end gap-2">
            <PandaMascot className="size-7 shrink-0" mood="thinking" />
            <p
              className="flex items-center gap-2 rounded-lg rounded-bl-sm bg-canvas px-3.5 py-2 text-caption text-ink-secondary"
              role="status"
            >
              <span aria-hidden="true" className="cnhsk-assistant-typing flex gap-1">
                <span className="size-1.5 rounded-pill bg-brand-700" />
                <span className="size-1.5 rounded-pill bg-brand-700" />
                <span className="size-1.5 rounded-pill bg-brand-700" />
              </span>
              Trợ lý đang trả lời…
            </p>
          </div>
        ) : null}
        {error ? (
          <p
            className="mt-3 rounded-md bg-danger-bg px-3 py-2 text-body-sm text-danger"
            role="alert"
          >
            {error}
          </p>
        ) : null}
      </div>

      <form
        className="border-t border-line px-3 pb-3 pt-2"
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
        <label className="sr-only" htmlFor="assistant-question">
          Câu hỏi về cách dùng CNHSK
        </label>
        <div className="flex items-end gap-2 rounded-lg border border-line bg-canvas py-1.5 pl-4 pr-1.5 focus-within:border-brand-600 focus-within:ring-2 focus-within:ring-brand-100">
          <textarea
            ref={inputRef}
            id="assistant-question"
            aria-describedby="assistant-question-count"
            className="max-h-32 min-h-10 flex-1 resize-none bg-transparent py-2 text-body text-ink border-0 shadow-none placeholder:text-ink-muted focus:outline-none focus:ring-0 focus-visible:outline-none focus-visible:ring-0"
            onChange={(event) => onDraftChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault()
                if (canSend) onSubmit()
              }
            }}
            placeholder="Hỏi trợ lý cách dùng CNHSK…"
            rows={1}
            value={draft}
          />
          <button
            aria-label="Gửi"
            className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-700 text-surface hover:bg-brand-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:cursor-not-allowed disabled:bg-line disabled:text-ink-muted"
            disabled={!canSend}
            type="submit"
          >
            <ArrowUp aria-hidden="true" className="size-5" />
          </button>
        </div>
        <p
          className="mt-1.5 flex justify-between gap-2 px-2 text-caption text-ink-muted"
          id="assistant-question-count"
        >
          <span>Enter để gửi · Shift + Enter xuống dòng</span>
          <span className={length > 2000 ? 'text-danger' : undefined}>{length}/2.000 ký tự</span>
        </p>
      </form>
    </section>
  )
}
