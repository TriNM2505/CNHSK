import type { ReactNode } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
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
}

export function DeckDetailView(props: DeckDetailViewProps) {
  const page = clientPage(props.page, props.deck?.cards.length ?? 0)
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title={props.deck?.name ?? 'Chi tiết bộ thẻ'}
        actions={
          <Button to="/library/decks" variant="outline">
            Về danh sách bộ thẻ
          </Button>
        }
      />
      {props.notice ? (
        <p role="status" className="text-body text-success">
          {props.notice}
        </p>
      ) : null}
      {props.state === 'loading' ? (
        <div aria-label="Đang tải chi tiết bộ thẻ">
          <Skeleton className="h-32" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <ErrorState
          title="Chưa tải được bộ thẻ"
          message={props.error ? flashcardError(props.error) : 'Vui lòng thử lại.'}
          requestId={props.error?.requestId}
          onRetry={props.onRetry}
        />
      ) : null}
      {props.mutationError ? (
        <div role="alert">
          <ErrorState
            title="Chưa hoàn tất thao tác"
            message={flashcardError(props.mutationError)}
            requestId={props.mutationError.requestId}
            onRetry={props.onMutationRetry}
          />
        </div>
      ) : null}
      {props.review ??
        (props.deck ? (
          <>
            {props.deck.description ? (
              <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
                {props.deck.description}
              </p>
            ) : null}
            <p className="text-body-sm text-ink-secondary">
              {props.deck.cardCount} thẻ · {props.deck.dueCount} đến hạn · Tối đa 500 thẻ trong bộ
            </p>
            <div className="flex flex-wrap gap-3">
              <Button disabled={props.busy} onClick={props.onReview}>
                Ôn bộ này
              </Button>
              <Button disabled={props.busy} variant="danger" onClick={props.onDeleteDeck}>
                Xoá bộ thẻ
              </Button>
            </div>
            <div className="grid items-start gap-6 lg:grid-cols-2">
              <section aria-label="Thẻ trong bộ" className="space-y-4">
                {props.state === 'ready' && props.deck.cards.length === 0 ? (
                  <EmptyState
                    title="Bộ thẻ trống"
                    description="Thêm thẻ đầu tiên bằng mặt trước và mặt sau của bạn."
                  />
                ) : null}
                {props.state === 'ready' && props.deck.cards.length > 0 ? (
                  <>
                    <ul className="space-y-3">
                      {props.deck.cards
                        .slice(page * flashcardPageSize, (page + 1) * flashcardPageSize)
                        .map((card) => (
                          <li
                            key={card.id}
                            className="space-y-3 rounded-md border border-line bg-surface p-4"
                          >
                            <p className="whitespace-pre-wrap break-words text-h3 text-ink">
                              {card.front}
                            </p>
                            <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
                              {card.back}
                            </p>
                            <p className="text-caption text-ink-secondary">
                              {card.dueAt ? (
                                <>
                                  Hạn ôn:{' '}
                                  <time dateTime={card.dueAt}>{flashcardDate(card.dueAt)}</time>
                                </>
                              ) : (
                                'Thẻ mới'
                              )}{' '}
                              · {card.reviewCount} lượt ôn · {card.lapseCount} lần quên
                            </p>
                            <div className="flex gap-2">
                              <Button
                                disabled={props.busy}
                                variant="outline"
                                onClick={() => props.onEditCard(card)}
                              >
                                Sửa thẻ
                              </Button>
                              <Button
                                disabled={props.busy}
                                variant="ghost"
                                onClick={() => props.onDeleteCard(card.id)}
                              >
                                Xoá thẻ
                              </Button>
                            </div>
                          </li>
                        ))}
                    </ul>
                    <Pagination
                      page={page + 1}
                      total={props.deck.cards.length}
                      size={flashcardPageSize}
                      onChange={(value) => props.onPageChange(value - 1)}
                    />
                  </>
                ) : null}
              </section>
              <div className="space-y-6">
                <form
                  aria-label="Sửa bộ thẻ"
                  noValidate
                  className="space-y-3 rounded-md border border-line bg-surface p-4"
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
                  className="space-y-3 rounded-md border border-line bg-surface p-4"
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
                  <div className="flex gap-2">
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
