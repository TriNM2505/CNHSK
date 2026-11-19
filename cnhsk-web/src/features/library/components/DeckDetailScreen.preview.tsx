import { useState } from 'react'

import type { PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout, PublicLayout } from '@/shared/layout'
import { ConfirmPanel, EmptyState, ErrorState, Skeleton } from '@/shared/ui'

import {
  emptyCardDraft,
  emptyDeckDraft,
  validateCardDraft,
  validateDeckDraft,
  type CardDraft,
} from '../flashcardForms'
import type { Card, DeckDetail } from '../types'
import { DeckDetailBanner, DeckDetailText } from './DeckDetailParts'
import { DeckCardContent } from './DeckDetailParts'
import { DeckDetailView } from './DeckDetailView'
import { FlashcardReviewView } from './FlashcardReviewView'

// Original fixtures. No API, storage, sharing or FSRS writes in this preview.
const sampleCards: Card[] = [
  {
    id: 901,
    front: '今天',
    back: 'jīntiān — hôm nay\n今天我看书。 Hôm nay tôi đọc sách.',
    dueAt: null,
    reviewCount: 0,
    lapseCount: 0,
  },
  {
    id: 902,
    front: '明天',
    back: 'míngtiān — ngày mai\n明天我去学校。 Ngày mai tôi đến trường.',
    dueAt: '2026-11-17T02:00:00Z',
    reviewCount: 3,
    lapseCount: 1,
  },
  {
    id: 903,
    front: '朋友',
    back: 'péngyou — bạn bè\n我和朋友学习中文。 Tôi học tiếng Trung cùng bạn.',
    dueAt: '2026-11-18T02:00:00Z',
    reviewCount: 2,
    lapseCount: 0,
  },
  {
    id: 904,
    front: '早上',
    back: 'zǎoshang — buổi sáng\n早上我喝水。 Buổi sáng tôi uống nước.',
    dueAt: null,
    reviewCount: 0,
    lapseCount: 0,
  },
  {
    id: 905,
    front: '看书',
    back: 'kàn shū — đọc sách\n我喜欢看书。 Tôi thích đọc sách.',
    dueAt: '2026-11-19T02:00:00Z',
    reviewCount: 4,
    lapseCount: 1,
  },
  {
    id: 906,
    front: '学习',
    back: 'xuéxí — học tập\n我们一起学习。 Chúng ta cùng học.',
    dueAt: null,
    reviewCount: 0,
    lapseCount: 0,
  },
]
const sampleDeck: DeckDetail = {
  id: 801,
  name: 'Từ dùng mỗi ngày',
  description: 'Một bộ từ nhỏ để kể về ngày của mình. Đọc ví dụ, tự đặt câu và ôn lại khi cần.',
  cardCount: 6,
  dueCount: 4,
  createdAt: '2026-11-17T02:00:00Z',
  cards: sampleCards,
}
interface DetailPreviewProps {
  state: PreviewState
  guest?: boolean
  review?: boolean
  flipped?: boolean
  editing?: boolean
}

function GuestDeckSample({ state }: { state: PreviewState }) {
  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <DeckDetailBanner name={sampleDeck.name} backTo="/__preview/frame/scr-028-ready/ready" />
      <div className="rounded-md border border-brand-100 bg-brand-50 p-4 text-body-sm text-brand-800">
        Bộ thẻ công khai · Chỉ đọc
      </div>
      {state === 'loading' ? (
        <div aria-label="Đang tải bộ thẻ">
          <Skeleton className="h-40" />
        </div>
      ) : state === 'error' ? (
        <ErrorState title="Chưa tải được bộ thẻ" message="Vui lòng thử lại." />
      ) : state === 'empty' ? (
        <EmptyState title="Bộ thẻ trống" description="Bộ này chưa có thẻ để xem." />
      ) : (
        <>
          <p className="max-w-reading text-body text-ink-secondary">
            <DeckDetailText text={sampleDeck.description!} />
          </p>
          <p className="text-body-sm text-ink-secondary">{sampleDeck.cardCount} thẻ</p>
          <section aria-label="Thẻ trong bộ">
            <ul className="space-y-3">
              {sampleCards.map((card) => (
                <li key={card.id}>
                  <DeckCardContent card={card} showSchedule={false} />
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}

function OwnerDeckSample({
  state,
  review = false,
  flipped = false,
  editing = false,
}: DetailPreviewProps) {
  const [deck, setDeck] = useState<DeckDetail | undefined>({
    ...sampleDeck,
    cards: state === 'empty' ? [] : sampleCards,
    cardCount: state === 'empty' ? 0 : 6,
    dueCount: state === 'empty' ? 0 : 4,
  })
  const [deckDraft, setDeckDraft] = useState({
    ...emptyDeckDraft(),
    name: sampleDeck.name,
    description: sampleDeck.description ?? '',
  })
  const [cardDraft, setCardDraft] = useState<CardDraft>(
    editing
      ? { id: sampleCards[0]!.id, front: sampleCards[0]!.front, back: sampleCards[0]!.back }
      : emptyCardDraft(),
  )
  const [page, setPage] = useState(0)
  const [deckValidation, setDeckValidation] = useState<string>()
  const [cardValidation, setCardValidation] = useState<string>()
  const [notice, setNotice] = useState('')
  const [confirm, setConfirm] = useState<{ kind: 'deck' | 'card'; id?: number }>()
  const [reviewing, setReviewing] = useState(review)
  const [batch, setBatch] = useState<Card[]>(sampleCards.slice(0, 4))
  const [index, setIndex] = useState(0)
  const [isFlipped, setFlipped] = useState(flipped)
  return (
    <DeckDetailView
      state={state === 'empty' ? 'ready' : state}
      deck={state === 'error' || state === 'loading' ? undefined : deck}
      error={
        new ApiError({
          status: 503,
          errorCode: 'UNAVAILABLE',
          message: 'Chưa tải được bộ thẻ. Vui lòng thử lại.',
        })
      }
      deckDraft={deckDraft}
      cardDraft={cardDraft}
      page={page}
      busy={false}
      notice={notice}
      deckValidation={deckValidation}
      cardValidation={cardValidation}
      onPageChange={setPage}
      onRetry={() => undefined}
      onDeckChange={(next) => {
        setDeckDraft(next)
        setDeckValidation(undefined)
      }}
      onCardChange={(next) => {
        setCardDraft(next)
        setCardValidation(undefined)
      }}
      onSaveDeck={() => {
        const error = validateDeckDraft(deckDraft)
        setDeckValidation(error)
        if (!error) {
          setDeck((current) =>
            current
              ? { ...current, name: deckDraft.name.trim(), description: deckDraft.description }
              : current,
          )
          setNotice('Đã lưu bộ thẻ mẫu.')
        }
      }}
      onSaveCard={() => {
        const error = validateCardDraft(cardDraft)
        setCardValidation(error)
        if (error || !deck) return
        const existing = deck.cards.find(({ id }) => id === cardDraft.id)
        const card: Card = {
          ...(existing ?? {
            id: Math.max(900, ...deck.cards.map(({ id }) => id)) + 1,
            dueAt: null,
            reviewCount: 0,
            lapseCount: 0,
          }),
          front: cardDraft.front,
          back: cardDraft.back,
        }
        const cards = existing
          ? deck.cards.map((item) => (item.id === card.id ? card : item))
          : [...deck.cards, card]
        setDeck({
          ...deck,
          cards,
          cardCount: cards.length,
          dueCount: deck.dueCount + (existing ? 0 : 1),
        })
        setCardDraft(emptyCardDraft())
        setNotice('Đã lưu thẻ mẫu. Không ghi dữ liệu thật.')
      }}
      onNewCard={() => {
        setCardDraft(emptyCardDraft())
        setCardValidation(undefined)
      }}
      onEditCard={(card) => {
        setCardDraft({ id: card.id, front: card.front, back: card.back })
        setCardValidation(undefined)
      }}
      onDeleteCard={(id) => setConfirm({ kind: 'card', id })}
      onDeleteDeck={() => setConfirm({ kind: 'deck' })}
      onReview={() => {
        const cards = deck?.cards.slice(0, Math.max(0, deck.dueCount)) ?? []
        setBatch(cards)
        setIndex(0)
        setFlipped(false)
        setReviewing(true)
        setNotice('')
      }}
      review={
        reviewing ? (
          <FlashcardReviewView
            state={batch.length ? 'ready' : 'empty'}
            card={batch[index]}
            remaining={Math.max(0, batch.length - index)}
            flipped={isFlipped}
            pending={false}
            completed={batch.length > 0 && index === batch.length}
            ahead={false}
            onFlip={() => setFlipped(true)}
            onRate={() => {
              setIndex((current) => Math.min(current + 1, batch.length))
              setFlipped(false)
            }}
            onRetry={() => undefined}
            onAhead={() => {
              setBatch(deck?.cards ?? [])
              setIndex(0)
              setFlipped(false)
            }}
            onClose={() => setReviewing(false)}
          />
        ) : undefined
      }
      confirmation={
        <ConfirmPanel
          open={confirm !== undefined}
          danger
          title={confirm?.kind === 'deck' ? 'Xoá bộ thẻ?' : 'Xoá thẻ?'}
          message="Thao tác này chỉ xoá dữ liệu mẫu trong bản xem trước."
          confirmLabel="Xác nhận xoá"
          onCancel={() => setConfirm(undefined)}
          onConfirm={() => {
            if (confirm?.kind === 'deck') {
              setDeck(undefined)
              setNotice('Đã xoá bộ thẻ mẫu.')
            } else if (deck && confirm) {
              const cards = deck.cards.filter(({ id }) => id !== confirm.id)
              setDeck({
                ...deck,
                cards,
                cardCount: cards.length,
                dueCount: Math.min(deck.dueCount, cards.length),
              })
              setPage(0)
              setNotice('Đã xoá thẻ mẫu.')
            }
            setConfirm(undefined)
          }}
        />
      }
    />
  )
}

export function DeckDetailScreenPreview(props: DetailPreviewProps) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: props.guest ? 'guest' : 'authenticated',
    user: props.guest
      ? null
      : {
          id: 101,
          displayName: 'Bạn học CNHSK',
          email: '',
          emailVerified: true,
          hskLevel: 1,
          roles: ['LEARNER'],
        },
  }
  return (
    <AuthContext.Provider value={auth}>
      {props.guest ? (
        <PublicLayout>
          <GuestDeckSample state={props.state} />
        </PublicLayout>
      ) : (
        <LearnerLayout>
          <OwnerDeckSample {...props} />
        </LearnerLayout>
      )}
    </AuthContext.Provider>
  )
}
