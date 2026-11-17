import { ArrowLeft, ArrowRight } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'

import { Button } from '@/shared/ui'

import type { Card } from '../types'
import { DeckCardContent } from './DeckDetailParts'

export function DeckCardCarousel({
  cards,
  actions,
  showSchedule = true,
  busy = false,
}: {
  cards: readonly Card[]
  actions?: (card: Card) => ReactNode
  showSchedule?: boolean
  busy?: boolean
}) {
  const [position, setPosition] = useState(0)
  useEffect(() => {
    setPosition((current) => Math.min(current, Math.max(0, cards.length - 1)))
  }, [cards.length])
  const index = Math.min(position, Math.max(0, cards.length - 1))
  const card = cards[index]
  if (!card) return null

  return (
    <div
      aria-label="Xem từng thẻ"
      aria-roledescription="carousel"
      className="mx-auto w-full min-w-0 max-w-reading space-y-4"
      role="group"
    >
      <ul className="min-w-0">
        <li className="deck-detail-enter min-w-0" key={card.id}>
          <DeckCardContent
            card={card}
            actions={actions?.(card)}
            showSchedule={showSchedule}
            prominent
          />
        </li>
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          disabled={busy || index === 0}
          onClick={() => setPosition(Math.max(0, index - 1))}
          variant="outline"
        >
          <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
          Trước
        </Button>
        <p
          aria-live="polite"
          aria-atomic="true"
          className="text-label tabular-nums text-ink-secondary"
        >
          Thẻ {index + 1} / {cards.length}
        </p>
        <Button
          disabled={busy || index + 1 === cards.length}
          onClick={() => setPosition(Math.min(cards.length - 1, index + 1))}
        >
          Tiếp theo
          <ArrowRight aria-hidden="true" className="ml-2 inline-block size-4 align-middle" />
        </Button>
      </div>
    </div>
  )
}
