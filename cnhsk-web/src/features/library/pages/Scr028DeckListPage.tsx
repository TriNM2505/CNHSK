import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { getTopics } from '@/features/vocab/api/topics'
import { ApiError, toApiError, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { createDeck, listDecks } from '../api/flashcards'
import { DeckListView } from '../components/DeckListView'
import { deckRequest, emptyDeckDraft, resourceId, validateDeckDraft } from '../flashcardForms'

function DeckListContent() {
  const navigate = useNavigate()
  const [draft, setDraft] = useState(emptyDeckDraft)
  const [page, setPage] = useState(0)
  const [validation, setValidation] = useState<string>()
  const [error, setError] = useState<ApiError>()
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  const decks = useApiQuery((signal) => listDecks(signal), [], {
    isEmpty: (data) => data.length === 0,
  })
  const topics = useApiQuery(
    (signal) => (draft.source === 'topic' ? getTopics(undefined, signal) : Promise.resolve([])),
    [draft.source],
    { isEmpty: (data) => data.length === 0 },
  )

  const create = async () => {
    if (busy.current) return
    const invalid = validateDeckDraft(draft, topics.state === 'ready' ? topics.data : undefined)
    setValidation(invalid)
    if (invalid) return
    busy.current = true
    setPending(true)
    setError(undefined)
    try {
      const created = await createDeck(deckRequest(draft))
      if (!alive.current) return
      if (resourceId(String(created.id)) === undefined)
        throw new ApiError({
          status: 0,
          errorCode: 'INVALID_RESPONSE',
          message: 'Phản hồi bộ thẻ không hợp lệ. Vui lòng thử lại.',
        })
      navigate(`/library/decks/${created.id}`)
    } catch (failure) {
      if (alive.current) setError(toApiError(failure))
    } finally {
      busy.current = false
      if (alive.current) setPending(false)
    }
  }

  return (
    <DeckListView
      state={decks.state}
      decks={decks.data ?? []}
      error={decks.error}
      draft={draft}
      validation={validation}
      createError={error}
      pending={pending}
      topics={topics.data ?? []}
      topicsState={topics.state}
      topicsError={topics.error}
      page={page}
      onPageChange={setPage}
      onChange={(next) => {
        setDraft(next)
        setValidation(undefined)
      }}
      onCreate={() => {
        void create()
      }}
      onRetry={decks.reload}
      onTopicsRetry={topics.reload}
    />
  )
}

export default function Scr028DeckListPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER'))
    return (
      <div className="space-y-4">
        <PageHeader title="Bộ thẻ của tôi" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState
            message="Cần đăng nhập với quyền học viên để xem bộ thẻ."
            onRetry={
              auth.status === 'error'
                ? () => {
                    void auth.reloadCurrentUser()
                  }
                : undefined
            }
          />
        )}
      </div>
    )
  return <DeckListContent key={auth.user.id} />
}
