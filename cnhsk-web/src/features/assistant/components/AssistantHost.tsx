import { MessageCircle } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { useCurrentUser } from '@/shared/auth'
import { toApiError } from '@/shared/api'
import type { ScreenId } from '@/app/screenRegistry'

import { askAssistant } from '../api/ask'
import { assistantPageContext } from '../pageContext'
import type { ChatTurn } from '../types'
import { AssistantPanel } from './AssistantPanel'

function AssistantSession({
  children,
  pageContext,
}: {
  children: ReactNode
  pageContext?: ScreenId
}) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<ChatTurn[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const controllerRef = useRef<AbortController | null>(null)

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
      setMessages((current) => [...current, { role: 'assistant', text: result.answer }])
    } catch (caughtError) {
      if (controllerRef.current !== controller || controller.signal.aborted) return
      setError(toApiError(caughtError).message)
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null
        setPending(false)
      }
    }
  }

  return (
    <div data-assistant-open={open} className={open ? 'min-w-0 lg:flex lg:items-start' : 'min-w-0'}>
      <div className="min-w-0 flex-1 pb-24">{children}</div>
      {open ? (
        <aside className="w-full shrink-0 p-4 lg:sticky lg:top-16 lg:w-80 xl:w-96">
          <AssistantPanel
            draft={draft}
            messages={messages}
            pending={pending}
            error={error}
            onDraftChange={setDraft}
            onSubmit={() => {
              void submit()
            }}
            onCollapse={() => setOpen(false)}
            onNewConversation={newConversation}
          />
        </aside>
      ) : (
        <button
          type="button"
          aria-label="Mở trợ lý CNHSK"
          aria-expanded={false}
          className="fixed bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 z-20 flex size-12 items-center justify-center rounded-full bg-brand-700 text-surface shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:bottom-6 sm:right-6"
          onClick={() => setOpen(true)}
        >
          <MessageCircle aria-hidden="true" className="size-6" />
        </button>
      )}
    </div>
  )
}

export function AssistantHost({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user } = useCurrentUser()
  const { hidden, pageContext } = assistantPageContext(location)
  if (hidden || !user?.roles.includes('LEARNER')) return <>{children}</>
  return (
    <AssistantSession key={user.id} pageContext={pageContext}>
      {children}
    </AssistantSession>
  )
}
