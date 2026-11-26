import { ArrowLeft, Layers3, Pencil, RotateCcw, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Pagination,
  Skeleton,
  Textarea,
} from '@/shared/ui'

import {
  characterCount,
  clientPage,
  flashcardDate,
  flashcardError,
  flashcardPageSize,
  type CardDraft,
  type DeckDraft,
} from '../flashcardForms'
import type { Card, DeckDetail } from '../types'
import { DeckDetailText } from './DeckDetailParts'
import { FlashcardViewer } from './FlashcardViewer'
import './LibraryView.css'

export interface DeckDetailViewProps {
  state: ApiQueryState
  error?: ApiError
  deck?: DeckDetail
  deckDraft: DeckDraft
  cardDraft: CardDraft
  page: number
  busy: boolean
  deckValidation?: string
  cardValidation?: string
  mutationError?: ApiError
  notice?: string
  review?: ReactNode
  confirmation?: ReactNode
  onPageChange: (page: number) => void
  onRetry: () => void
  onMutationRetry?: () => void
  onDeckChange: (draft: DeckDraft) => void
  onCardChange: (draft: CardDraft) => void
  onSaveDeck: () => void
  onSaveCard: () => void
  onNewCard: () => void
  onEditCard: (card: Card) => void
  onDeleteCard: (id: number) => void
  onDeleteDeck: () => void
  onReview: () => void
  /** Leaves the review and returns to browsing the deck. */
  onCloseReview?: () => void
}

/** A study mode tile at the top of a deck, like the mode switcher of popular flashcard apps. */
function ModeTile({
  label,
  hint,
  active,
  disabled,
  icon,
  onClick,
}: {
  label: string
  hint: string
  active: boolean
  disabled?: boolean
  icon: ReactNode
  onClick: () => void
}) {
  return (
    <button
      aria-pressed={active}
      className={`flex min-h-16 min-w-0 items-center justify-center gap-3 rounded-lg border-b-4 px-4 text-h3 shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-60 ${
        active ? 'border-brand-600 bg-surface text-ink' : 'border-transparent bg-surface text-ink-secondary hover:border-brand-100'
      }`}
      disabled={disabled}
      onClick={onClick}
      title={hint}
      type="button"
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">{icon}</span>
      {label}
    </button>
  )
}

export function DeckDetailView(props: DeckDetailViewProps) {
  const page = clientPage(props.page, props.deck?.cards.length ?? 0)
  const reviewing = props.review !== undefined
  return (
    <div className="library-screen mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <header className="space-y-3">
        <Button size="sm" to="/library/decks" variant="ghost">
          <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
          Về danh sách bộ thẻ
        </Button>
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            <h1 className="break-words text-h1 text-ink">
              <DeckDetailText text={props.deck?.name ?? 'Chi tiết bộ thẻ'} />
            </h1>
            {props.deck ? (
              <p className="text-body-sm text-ink-secondary">
                {props.deck.cardCount} thẻ · {props.deck.dueCount} đến hạn · Tối đa 500 thẻ trong bộ
              </p>
            ) : null}
          </div>
          {props.deck ? (
            <Button disabled={props.busy} onClick={props.onDeleteDeck} variant="danger">
              <Trash2 aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Xoá bộ thẻ
            </Button>
          ) : null}
        </div>
        {props.deck?.description ? (
          <p className="max-w-reading whitespace-pre-wrap break-words text-body text-ink-secondary">
            <DeckDetailText text={props.deck.description} />
          </p>
        ) : null}
      </header>
      {props.notice ? (
        <p className="text-body text-success" role="status">
          {props.notice}
        </p>
      ) : null}
      {props.state === 'loading' ? (
        <div aria-label="Đang tải chi tiết bộ thẻ">
          <Skeleton className="h-72" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <ErrorState
          message={props.error ? flashcardError(props.error) : 'Vui lòng thử lại.'}
          onRetry={props.onRetry}
          requestId={props.error?.requestId}
          title="Chưa tải được bộ thẻ"
        />
      ) : null}
      {props.mutationError ? (
        <div role="alert">
          <ErrorState
            message={flashcardError(props.mutationError)}
            onRetry={props.onMutationRetry}
            requestId={props.mutationError.requestId}
            title="Chưa hoàn tất thao tác"
          />
        </div>
      ) : null}
      {props.deck ? (
        <div aria-label="Cách học" className="grid min-w-0 grid-cols-2 gap-3" role="group">
          <ModeTile
            active={!reviewing}
            hint="Xem và lật từng thẻ, không đổi lịch ôn"
            icon={<Layers3 aria-hidden="true" className="size-5" />}
            label="Thẻ ghi nhớ"
            onClick={() => {
              if (reviewing) props.onCloseReview?.()
            }}
          />
          <ModeTile
            active={reviewing}
            disabled={props.busy}
            hint="Ôn các thẻ đến hạn và chấm mức nhớ"
            icon={<RotateCcw aria-hidden="true" className="size-5" />}
            label="Học"
            onClick={() => {
              if (!reviewing) props.onReview()
            }}
          />
        </div>
      ) : null}
      {props.review ??
        (props.deck ? (
          <>
            {props.state === 'ready' && props.deck.cards.length > 0 ? (
              <FlashcardViewer cards={props.deck.cards} />
            ) : null}
            <div className="deck-detail-workspace min-w-0 space-y-6">
              <section aria-label="Thẻ trong bộ" className="min-w-0 space-y-3">
                <h2 className="text-h3 text-ink">Thẻ trong bộ ({props.deck.cards.length})</h2>
                {props.state === 'ready' && props.deck.cards.length === 0 ? (
                  <EmptyState
                    description="Thêm thẻ đầu tiên bằng mặt trước và mặt sau của bạn."
                    title="Bộ thẻ trống"
                  />
                ) : null}
                {props.state === 'ready' && props.deck.cards.length > 0 ? (
                  <>
                    <ul className="space-y-2">
                      {props.deck.cards
                        .slice(page * flashcardPageSize, (page + 1) * flashcardPageSize)
                        .map((card) => (
                          <li
                            className="grid min-w-0 gap-3 rounded-lg bg-surface p-4 shadow-sm sm:grid-cols-[minmax(0,2fr)_minmax(0,3fr)_auto] sm:items-start sm:gap-6"
                            key={card.id}
                          >
                            <p className="whitespace-pre-wrap break-words text-h3 text-ink sm:border-r sm:border-line sm:pr-6">
                              <DeckDetailText text={card.front} />
                            </p>
                            <div className="min-w-0 space-y-1">
                              <p className="whitespace-pre-wrap break-words text-body text-ink">
                                <DeckDetailText text={card.back} />
                              </p>
                              <p className="text-caption text-ink-muted">
                                {card.dueAt ? (
                                  <>
                                    Hạn ôn: <time dateTime={card.dueAt}>{flashcardDate(card.dueAt)}</time>
                                  </>
                                ) : (
                                  'Thẻ mới'
                                )}{' '}
                                · {card.reviewCount} lượt ôn · {card.lapseCount} lần quên
                              </p>
                            </div>
                            <div className="flex gap-1 sm:justify-end">
                              <Button
                                aria-label="Sửa thẻ"
                                className="min-h-touch min-w-touch"
                                disabled={props.busy}
                                onClick={() => props.onEditCard(card)}
                                size="sm"
                                title="Sửa thẻ"
                                variant="ghost"
                              >
                                <Pencil aria-hidden="true" className="size-4" />
                              </Button>
                              <Button
                                aria-label="Xoá thẻ"
                                className="min-h-touch min-w-touch"
                                disabled={props.busy}
                                onClick={() => props.onDeleteCard(card.id)}
                                size="sm"
                                title="Xoá thẻ"
                                variant="ghost"
                              >
                                <Trash2 aria-hidden="true" className="size-4" />
                              </Button>
                            </div>
                          </li>
                        ))}
                    </ul>
                    <Pagination
                      onChange={(value) => props.onPageChange(value - 1)}
                      page={page + 1}
                      size={flashcardPageSize}
                      total={props.deck.cards.length}
                    />
                  </>
                ) : null}
              </section>              <div className="deck-detail-editors grid min-w-0 items-start gap-4 lg:grid-cols-2">
                <form
                  aria-label="Sửa bộ thẻ"
                  noValidate
                  className="space-y-3 rounded-md border border-brand-100 bg-surface p-4 shadow-sm"
                  onSubmit={(event) => {
                    event.preventDefault()
                    props.onSaveDeck()
                  }}
                >
                  <h2 className="text-h3 text-ink">Thông tin bộ thẻ</h2>
                  <fieldset disabled={props.busy}>
                    <legend className="sr-only">Tên và mô tả bộ thẻ</legend>
                    <FormField
                      htmlFor="edit-deck-name"
                      label="Tên bộ thẻ"
                      required
                      hint={`${characterCount(props.deckDraft.name)}/100 ký tự`}
                    >
                      <Input
                        className="font-hanzi"
                        value={props.deckDraft.name}
                        onChange={(event) =>
                          props.onDeckChange({ ...props.deckDraft, name: event.target.value })
                        }
                      />
                    </FormField>
                    <FormField
                      htmlFor="edit-deck-description"
                      label="Mô tả"
                      hint={`${characterCount(props.deckDraft.description)}/500 ký tự`}
                    >
                      <Textarea
                        className="min-h-24 font-hanzi"
                        value={props.deckDraft.description}
                        onChange={(event) =>
                          props.onDeckChange({
                            ...props.deckDraft,
                            description: event.target.value,
                          })
                        }
                      />
                    </FormField>
                  </fieldset>
                  {props.deckValidation ? (
                    <p role="alert" className="text-body-sm text-danger">
                      {props.deckValidation}
                    </p>
                  ) : null}
                  <Button type="submit" disabled={props.busy}>
                    Lưu bộ thẻ
                  </Button>
                </form>
                <form
                  aria-label="Soạn thẻ"
                  noValidate
                  className="space-y-3 rounded-md border border-brand-100 bg-surface p-4 shadow-sm"
                  onSubmit={(event) => {
                    event.preventDefault()
                    props.onSaveCard()
                  }}
                >
                  <h2 className="text-h3 text-ink">
                    {props.cardDraft.id === undefined ? 'Thẻ mới' : 'Sửa thẻ'}
                  </h2>
                  <fieldset disabled={props.busy}>
                    <legend className="sr-only">Hai mặt của thẻ</legend>
                    <FormField
                      htmlFor="card-front"
                      label="Mặt trước"
                      required
                      hint={`${characterCount(props.cardDraft.front)}/200 ký tự`}
                    >
                      <Textarea
                        className="min-h-24 font-hanzi"
                        value={props.cardDraft.front}
                        onChange={(event) =>
                          props.onCardChange({ ...props.cardDraft, front: event.target.value })
                        }
                      />
                    </FormField>
                    <FormField
                      htmlFor="card-back"
                      label="Mặt sau"
                      required
                      hint={`${characterCount(props.cardDraft.back)}/2.000 ký tự`}
                    >
                      <Textarea
                        className="min-h-24 font-hanzi"
                        value={props.cardDraft.back}
                        onChange={(event) =>
                          props.onCardChange({ ...props.cardDraft, back: event.target.value })
                        }
                      />
                    </FormField>
                  </fieldset>
                  {props.cardValidation ? (
                    <p role="alert" className="text-body-sm text-danger">
                      {props.cardValidation}
                    </p>
                  ) : null}
                  <div className="flex flex-wrap gap-2">
                    <Button type="submit" disabled={props.busy}>
                      Lưu thẻ
                    </Button>
                    <Button disabled={props.busy} variant="outline" onClick={props.onNewCard}>
                      Nhập thẻ mới
                    </Button>
                  </div>
                </form>
              </div>
            </div>
          </>
        ) : null)}
      {props.confirmation}
    </div>
  )
}