import { ArrowLeft, Clock3, Layers3, RotateCcw } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'

import { Button, Card as CardPanel, HanziText, PageHeader } from '@/shared/ui'

import { flashcardDate } from '../flashcardForms'
import type { Card } from '../types'
import { DictionaryLandscapeArt } from './DictionaryLandscapeArt'
import { LibraryHeaderArt } from './LibraryHeaderArt'
import './DeckDetailView.css'

export function DeckDetailText({ text }: { text: string }) {
  return text.split(/([\p{Script=Han}\u3000-\u303f\uff00-\uffef]+)/u).map((part, index) =>
    /[\p{Script=Han}\u3000-\u303f\uff00-\uffef]/u.test(part) ? (
      <HanziText className="detail-deck-hanzi" key={index} size="sm">
        {part}
      </HanziText>
    ) : (
      part
    ),
  )
}

export function DeckDetailBanner({
  name,
  actions,
  backTo = '/library/decks',
}: {
  name?: string
  actions?: ReactNode
  backTo?: string
}) {
  return (
    <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
      <PageHeader
        className="relative z-nav mb-0"
        title={
          <span className="inline-flex min-w-0 max-w-full items-center gap-3">
            <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
            <span className="min-w-0 break-words">
              <DeckDetailText text={name ?? 'Chi tiết bộ thẻ'} />
            </span>
          </span>
        }
        subtitle="Mỗi lần ôn, thêm một điều được ghi nhớ."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button to={backTo} variant="outline">
              <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Về danh sách bộ thẻ
            </Button>
            {actions}
          </div>
        }
      />
      <LibraryHeaderArt />
    </div>
  )
}

export function DeckCardContent({
  card,
  actions,
  showSchedule = true,
  prominent = false,
}: {
  card: Card
  actions?: ReactNode
  showSchedule?: boolean
  prominent?: boolean
}) {
  const [flipped, setFlipped] = useState(false)
  useEffect(() => {
    setFlipped(false)
  }, [card.id, card.front, card.back])

  return (
    <CardPanel
      className={`${prominent ? 'deck-study-card ' : ''}group relative isolate h-full min-w-0 overflow-hidden border-brand-100 p-4 shadow-sm transition duration-200 hover:border-brand-600 hover:shadow-md focus-within:border-brand-600 motion-safe:hover:-translate-y-0.5`}
    >
      <DictionaryLandscapeArt bridge={card.id % 2 === 0} />
      <div className="relative z-base min-w-0 space-y-3">
        <Button
          aria-label={`${flipped ? 'Lật về mặt trước' : 'Lật thẻ'} ${card.front}`}
          aria-pressed={flipped}
          className="deck-flip-trigger min-h-40 w-full rounded-md border-brand-100 bg-brand-50 p-4 text-left hover:bg-brand-100"
          onClick={() => setFlipped((current) => !current)}
          variant="ghost"
        >
          <span className="deck-flip-face block space-y-3" key={flipped ? 'back' : 'front'}>
            <span className="flex items-center gap-2 text-caption text-brand-700">
              <Layers3 aria-hidden="true" className="size-4" />
              {flipped ? 'Mặt sau' : 'Mặt trước'}
            </span>
            <span
              className={
                flipped
                  ? 'block whitespace-pre-wrap break-words text-body text-ink'
                  : 'deck-card-front block whitespace-pre-wrap break-words text-h3 text-ink'
              }
            >
              <DeckDetailText text={flipped ? card.back : card.front} />
            </span>
            <span className="flex items-center gap-2 text-caption text-brand-700">
              <RotateCcw aria-hidden="true" className="size-3.5 shrink-0" />
              {flipped ? 'Nhấn để trở về mặt trước' : 'Nhấn để xem mặt sau'}
            </span>
          </span>
        </Button>
        {showSchedule ? (
          <p className="text-caption text-ink-secondary">
            <Clock3 aria-hidden="true" className="mr-1 inline-block size-3.5 align-middle" />
            {card.dueAt ? (
              <>
                Hạn ôn: <time dateTime={card.dueAt}>{flashcardDate(card.dueAt)}</time>
              </>
            ) : (
              'Thẻ mới'
            )}{' '}
            · {card.reviewCount} lượt ôn · {card.lapseCount} lần quên
          </p>
        ) : null}
        {actions}
      </div>
    </CardPanel>
  )
}
