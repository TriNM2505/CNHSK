import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { ApiError, toApiError, useApiQuery, type ApiQueryState } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ConfirmPanel, ErrorState, PageHeader } from '@/shared/ui'

import {
  addCard,
  deleteCard,
  deleteDeck,
  getDeck,
  listDueCards,
  reviewCard,
  updateCard,
  updateDeck,
} from '../api/flashcards'
import { DeckDetailView } from '../components/DeckDetailView'
import { FlashcardReviewView } from '../components/FlashcardReviewView'
import {
  cardRequest,
  deckRequest,
  emptyCardDraft,
  emptyDeckDraft,
  resourceId,
  validateCardDraft,
  validateDeckDraft,
  type CardDraft,
  type DeckDraft,
} from '../flashcardForms'
import type { Card, DeckDetail, DeckSummary, ReviewRating, ReviewResult } from '../types'

type WriteKind = 'deck' | 'card' | 'deleteCard' | 'deleteDeck' | 'rating'
type Confirmation = { kind: 'deleteCard'; id: number } | { kind: 'deleteDeck' }
type ReviewState = {
  state: ApiQueryState
  cards: Card[]
  index: number
  flipped: boolean
  ahead: boolean
  error?: ApiError
  ratingError?: ApiError
  result?: ReviewResult
  completed: boolean
}
const initialReview: ReviewState = {
  state: 'loading',
  cards: [],
  index: 0,
  flipped: false,
  ahead: false,
  completed: false,
}

function DeckDetailContent({ rawId }: { rawId: string }) {
  const id = resourceId(rawId)
  const navigate = useNavigate()
  const [loaded, setLoaded] = useState<DeckDetail>()
  const [deckDraft, setDeckDraft] = useState<DeckDraft>(emptyDeckDraft)
  const [cardDraft, setCardDraft] = useState<CardDraft>(emptyCardDraft)
  const [deckValidation, setDeckValidation] = useState<string>()
  const [cardValidation, setCardValidation] = useState<string>()
  const [error, setError] = useState<ApiError>()
  const [pending, setPending] = useState<WriteKind>()
  const [confirmation, setConfirmation] = useState<Confirmation>()
  const [failedDelete, setFailedDelete] = useState<Confirmation>()
  const [review, setReview] = useState<ReviewState>()
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const initialized = useRef(false)
  const alive = useRef(true)
  const busy = useRef(false)
  const reviewController = useRef<AbortController>()
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      reviewController.current?.abort()
    }
  }, [])
  const query = useApiQuery(
    (signal) =>
      id === undefined
        ? Promise.reject(
            new ApiError({
              status: 403,
              errorCode: 'COLLECTION_NOT_OWNED',
              message: 'Bạn không có quyền truy cập bộ thẻ này.',
            }),
          )
        : getDeck(id, signal),
    [id],
  )
  useEffect(() => {
    if (!query.data || query.state !== 'ready') return
    setLoaded(query.data)
    if (!initialized.current) {
      initialized.current = true
      setDeckDraft({
        ...emptyDeckDraft(),
        name: query.data.name,
        description: query.data.description ?? '',
      })
    }
  }, [query.data, query.state])

  const write = async (kind: Exclude<WriteKind, 'rating'>, cardId?: number) => {
    if (busy.current || id === undefined) return
    if (kind === 'deck') {
      const invalid = validateDeckDraft(deckDraft)
      setDeckValidation(invalid)
      if (invalid) return
    }
    if (kind === 'card') {
      const invalid = validateCardDraft(cardDraft)
      setCardValidation(invalid)
      if (invalid) return
    }
    busy.current = true
    setPending(kind)
    setError(undefined)
    setFailedDelete(undefined)
    setNotice('')
    try {
      if (kind === 'deck') {
        const saved: DeckSummary = await updateDeck(
          id,
          deckRequest({ ...deckDraft, source: 'empty' }),
        )
        if (!alive.current) return
        setDeckDraft({
          ...emptyDeckDraft(),
          name: saved.name,
          description: saved.description ?? '',
        })
        setNotice('Đã lưu bộ thẻ.')
      } else if (kind === 'card') {
        if (cardDraft.id === undefined) await addCard(id, cardRequest(cardDraft))
        else await updateCard(id, cardDraft.id, cardRequest(cardDraft))
        if (!alive.current) return
        setCardDraft(emptyCardDraft())
        setNotice('Đã lưu thẻ.')
      } else if (kind === 'deleteCard' && cardId !== undefined) {
        await deleteCard(id, cardId)
        if (!alive.current) return
        if (cardDraft.id === cardId) setCardDraft(emptyCardDraft())
        setConfirmation(undefined)
        setNotice('Đã xoá thẻ.')
      } else if (kind === 'deleteDeck') {
        await deleteDeck(id)
        if (!alive.current) return
        navigate('/library/decks')
        return
      }
      if (alive.current) query.reload()
    } catch (failure) {
      if (!alive.current) return
      setError(toApiError(failure))
      if (kind === 'deleteDeck' || (kind === 'deleteCard' && cardId !== undefined)) {
        setFailedDelete(kind === 'deleteDeck' ? { kind } : { kind, id: cardId! })
        setConfirmation(undefined)
      }
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  const loadReview = async (ahead = false) => {
    if (busy.current || id === undefined) return
    reviewController.current?.abort()
    const controller = new AbortController()
    reviewController.current = controller
    setReview({ ...initialReview, ahead })
    setNotice('')
    try {
      const cards = await listDueCards(id, ahead, controller.signal)
      if (!alive.current || controller.signal.aborted) return
      setReview({
        ...initialReview,
        ahead,
        cards: cards.slice(0, 100),
        state: cards.length ? 'ready' : 'empty',
      })
    } catch (failure) {
      if (alive.current && !controller.signal.aborted)
        setReview({ ...initialReview, ahead, state: 'error', error: toApiError(failure) })
    }
  }

  const rate = async (rating: ReviewRating) => {
    const card = review?.cards[review.index]
    if (busy.current || !card || !review?.flipped || review.state !== 'ready') return
    busy.current = true
    setPending('rating')
    setReview({ ...review, ratingError: undefined })
    try {
      const result = await reviewCard(card.id, { rating })
      if (!alive.current) return
      const index = review.index + 1
      setReview({
        ...review,
        index,
        flipped: false,
        result,
        ratingError: undefined,
        completed: index === review.cards.length,
      })
    } catch (failure) {
      if (alive.current) setReview({ ...review, ratingError: toApiError(failure) })
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  return (
    <DeckDetailView
      state={query.state}
      error={query.error}
      deck={query.state === 'ready' ? query.data : loaded}
      deckDraft={deckDraft}
      cardDraft={cardDraft}
      page={page}
      busy={pending !== undefined}
      deckValidation={deckValidation}
      cardValidation={cardValidation}
      mutationError={error}
      notice={notice}
      onPageChange={setPage}
      onRetry={query.reload}
      onMutationRetry={failedDelete ? () => setConfirmation(failedDelete) : undefined}
      onDeckChange={(draft) => {
        setDeckDraft(draft)
        setDeckValidation(undefined)
      }}
      onCardChange={(draft) => {
        setCardDraft(draft)
        setCardValidation(undefined)
      }}
      onSaveDeck={() => {
        void write('deck')
      }}
      onSaveCard={() => {
        void write('card')
      }}
      onNewCard={() => {
        setCardDraft(emptyCardDraft())
        setCardValidation(undefined)
        setError(undefined)
      }}
      onEditCard={(card) => {
        setCardDraft({ id: card.id, front: card.front, back: card.back })
        setCardValidation(undefined)
        setError(undefined)
      }}
      onDeleteCard={(cardId) => setConfirmation({ kind: 'deleteCard', id: cardId })}
      onDeleteDeck={() => setConfirmation({ kind: 'deleteDeck' })}
      onReview={() => {
        void loadReview()
      }}
      review={
        review ? (
          <FlashcardReviewView
            {...review}
            card={review.cards[review.index]}
            remaining={review.cards.length - review.index}
            pending={pending === 'rating'}
            onFlip={() => {
              if (!busy.current) setReview({ ...review, flipped: true })
            }}
            onRate={(rating) => {
              void rate(rating)
            }}
            onRetry={() => {
              void loadReview(review.ahead)
            }}
            onAhead={() => {
              void loadReview(true)
            }}
            onClose={() => {
              if (busy.current) return
              reviewController.current?.abort()
              setReview(undefined)
              query.reload()
            }}
          />
        ) : undefined
      }
      confirmation={
        <ConfirmPanel
          open={confirmation !== undefined}
          title={confirmation?.kind === 'deleteDeck' ? 'Xoá bộ thẻ?' : 'Xoá thẻ?'}
          message={
            confirmation?.kind === 'deleteDeck'
              ? 'Bộ thẻ sẽ được xoá khỏi danh sách của bạn.'
              : 'Thẻ sẽ được xoá khỏi bộ này.'
          }
          confirmLabel="Xác nhận xoá"
          danger
          pending={pending === 'deleteCard' || pending === 'deleteDeck'}
          onCancel={() => setConfirmation(undefined)}
          onConfirm={() => {
            if (confirmation)
              void write(
                confirmation.kind,
                confirmation.kind === 'deleteCard' ? confirmation.id : undefined,
              )
          }}
        />
      }
    />
  )
}

export default function Scr029DeckDetailPage() {
  const auth = useCurrentUser()
  const { id = '' } = useParams()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER'))
    return (
      <div className="space-y-4">
        <PageHeader title="Chi tiết bộ thẻ" />
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
  return <DeckDetailContent key={`${auth.user.id}:${id}`} rawId={id} />
}
