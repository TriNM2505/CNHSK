import { MessageCircle } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { useCurrentUser } from '@/shared/auth'
import { toApiError } from '@/shared/api'
import type { ScreenId } from '@/app/screenRegistry'

import { askAssistant } from '../api/ask'
import { assistantPageContext } from '../pageContext'
import { AssistantPanel, type AssistantMessage } from './AssistantPanel'
import { PandaMascot } from './PandaMascot'

function AssistantSession({
  children,
  pageContext,
}: {
  children: ReactNode
  pageContext?: ScreenId
}) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<AssistantMessage[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const controllerRef = useRef<AbortController | null>(null)
  const launcherRef = useRef<HTMLButtonElement>(null)
  const restoreFocusRef = useRef(false)

  useEffect(() => {
    if (!open && restoreFocusRef.current) {
      restoreFocusRef.current = false
      launcherRef.current?.focus()
    }
  }, [open])

  function collapse() {
    restoreFocusRef.current = true
    setOpen(false)
  }

  useEffect(
    () => () => {
      controllerRef.current?.abort()
      controllerRef.current = null
    },
    [],
  )

  function newConversation() {
    controllerRef.current?.abort()
    controllerRef.current = null
    setMessages([])
    setDraft('')
    setPending(false)
    setError(undefined)
  }

  async function submit() {
    const question = draft.trim()
    if (!question || Array.from(draft).length > 2000 || controllerRef.current) return
    const controller = new AbortController()
    controllerRef.current = controller
    const history = messages.slice(-10).map(({ role, text }) => ({
      role,
      text: Array.from(text).slice(0, 4000).join(''),
    }))
    setMessages((current) => [...current, { role: 'user', text: question }])
    setDraft('')
    setPending(true)
    setError(undefined)
    try {
      const result = await askAssistant(
        { question, history, ...(pageContext ? { pageContext } : {}) },
        controller.signal,
      )
      if (controllerRef.current !== controller || controller.signal.aborted) return
      setMessages((current) => [
        ...current,
        { role: 'assistant', text: result.answer, outOfScope: result.outOfScope },
      ])
    } catch (caughtError) {
      if (controllerRef.current !== controller || controller.signal.aborted) return
      const apiError = toApiError(caughtError)
      switch (apiError.errorCode) {
        case 'RATE_LIMITED':
          setError('Bạn đã hỏi nhiều trong hôm nay, quay lại ngày mai')
          break
        case 'ASSISTANT_UNAVAILABLE':
          setError('Trợ lý tạm thời chưa trả lời được, bạn thử lại sau')
          break
        default:
          setError(apiError.message)
      }
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null
        setPending(false)
      }
    }
  }

  return (
    // The card floats over the page like a chat widget, so opening it never narrows the content.
    <div data-assistant-open={open} className="min-w-0">
      <div className="min-w-0 flex-1 pb-24">{children}</div>
      {open ? (
        <aside className="fixed inset-x-3 bottom-[calc(5rem+env(safe-area-inset-bottom))] top-20 z-dropdown sm:inset-x-auto sm:bottom-6 sm:right-6 sm:top-auto sm:h-[min(640px,calc(100dvh-7rem))] sm:w-[400px]">
          <AssistantPanel
            draft={draft}
            messages={messages}
            pending={pending}
            error={error}
            onDraftChange={setDraft}
            onSubmit={() => {
              void submit()
            }}
            onCollapse={collapse}
            onNewConversation={newConversation}
          />
        </aside>
      ) : (
        <button
          ref={launcherRef}
          type="button"
          aria-label="Mở trợ lý CNHSK"
          aria-expanded={false}
          className="cnhsk-panda-launcher fixed bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 z-nav flex size-16 items-center justify-center rounded-pill border-2 border-brand-100 bg-surface shadow-md transition-transform hover:-translate-y-0.5 hover:shadow-overlay focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:bottom-6 sm:right-6"
          onClick={() => setOpen(true)}
          title="Hỏi trợ lý CNHSK"
        >
          <PandaMascot className="size-14" />
          <span className="absolute -bottom-0.5 -right-0.5 flex size-6 items-center justify-center rounded-pill bg-brand-700 text-surface ring-2 ring-surface">
            <MessageCircle aria-hidden="true" className="size-3.5" />
          </span>
        </button>
      )}
    </div>
  )
}

export function AssistantHost({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user } = useCurrentUser()
  const { pageContext } = assistantPageContext(location)
  if (!user?.roles.includes('LEARNER')) return <>{children}</>
  return (
    <AssistantSession key={user.id} pageContext={pageContext}>
      {children}
    </AssistantSession>
  )
}
