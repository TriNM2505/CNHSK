import {
  ArrowRight,
  CheckCircle2,
  Layers3,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Trash2,
} from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, EmptyState, ErrorState, FormField, Input, Skeleton, Textarea } from '@/shared/ui'

import { characterCount, flashcardError, type CardDraft, type DeckDraft } from '../flashcardForms'
import type { Card, DeckDetail } from '../types'
import { DeckDetailBanner, DeckDetailText } from './DeckDetailParts'
import { DeckCardCarousel } from './DeckCardCarousel'
import { DictionaryLandscapeArt } from './DictionaryLandscapeArt'

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
  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <DeckDetailBanner
        name={props.deck?.name}
        actions={
          props.deck && props.review == null ? (
            <Button className="rounded-pill" disabled={props.busy} onClick={props.onReview}>
              <RotateCcw aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Ôn bộ này
            </Button>
          ) : undefined
        }
      />
      {props.notice ? (
        <p
          role="status"
          className="deck-detail-enter flex items-start gap-2 rounded-md bg-success-bg p-3 text-body text-success"
        >
          <CheckCircle2 aria-hidden="true" className="mt-1 size-4 shrink-0" />
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
            <div className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-surface p-4">
              <DictionaryLandscapeArt bridge />
              <div className="relative z-base space-y-3">
                {props.deck.description ? (
                  <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
                    <DeckDetailText text={props.deck.description} />
                  </p>
                ) : null}
                <p className="text-body-sm text-ink-secondary">
                  <Layers3
                    aria-hidden="true"
                    className="mr-2 inline-block size-4 align-middle text-brand-600"
                  />
                  {props.deck.cardCount} thẻ · {props.deck.dueCount} đến hạn · Tối đa 500 thẻ trong
                  bộ
                </p>
                <div className="flex flex-wrap gap-3">
                  <Button disabled={props.busy} variant="danger" onClick={props.onDeleteDeck}>
                    <Trash2 aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                    Xoá bộ thẻ
                  </Button>
                </div>
              </div>
            </div>
            <div className="deck-detail-workspace grid min-w-0 items-start gap-4 lg:grid-cols-2 xl:grid-cols-3">
              <section
                aria-label="Thẻ trong bộ"
                className="deck-detail-cards deck-detail-enter min-w-0 space-y-4 xl:col-span-2"
              >
                {props.state === 'ready' && props.deck.cards.length === 0 ? (
                  <EmptyState
                    className="border-brand-100 py-6"
                    icon={<Layers3 aria-hidden="true" className="size-5" />}
                    title="Bộ thẻ trống"
                    description="Thêm thẻ đầu tiên bằng mặt trước và mặt sau của bạn."
                  />
                ) : null}
                {props.state === 'ready' && props.deck.cards.length > 0 ? (
                  <DeckCardCarousel
                    cards={props.deck.cards}
                    busy={props.busy}
                    actions={(card) => (
                      <div className="flex flex-wrap gap-2">
                        <Button
                          disabled={props.busy}
                          variant="outline"
                          onClick={() => props.onEditCard(card)}
                        >
                          <Pencil
                            aria-hidden="true"
                            className="mr-2 inline-block size-4 align-middle"
                          />
                          Sửa thẻ
                        </Button>
                        <Button
                          disabled={props.busy}
                          variant="ghost"
                          onClick={() => props.onDeleteCard(card.id)}
                        >
                          <Trash2
                            aria-hidden="true"
                            className="mr-2 inline-block size-4 align-middle"
                          />
                          Xoá thẻ
                        </Button>
                      </div>
                    )}
                  />
                ) : null}
              </section>
              <div className="deck-detail-editors deck-detail-enter min-w-0 space-y-4 lg:sticky lg:top-20">
                <form
                  aria-label="Sửa bộ thẻ"
                  noValidate
                  className="space-y-3 rounded-md border border-brand-100 bg-surface p-4 shadow-sm"
                  onSubmit={(event) => {
                    event.preventDefault()
                    props.onSaveDeck()
                  }}
                >
                  <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                    <Pencil aria-hidden="true" className="size-5 text-brand-600" />
                    Thông tin bộ thẻ
                  </h2>
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
                  <Button className="w-full rounded-pill" type="submit" disabled={props.busy}>
                    <Save aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
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
                  <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                    <Plus aria-hidden="true" className="size-5 text-brand-600" />
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
                      <Save aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                      Lưu thẻ
                    </Button>
                    <Button disabled={props.busy} variant="outline" onClick={props.onNewCard}>
                      <ArrowRight
                        aria-hidden="true"
                        className="mr-2 inline-block size-4 align-middle"
                      />
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
