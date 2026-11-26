import { ArrowLeft, ArrowRight, Maximize, Minimize, Pause, Play, Shuffle } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'

import type { Card } from '../types'
import { FlashcardStage } from './FlashcardStage'

/** Seconds a side stays up while auto-play runs: front, then back, then the next card. */
const AUTOPLAY_MS = 3000

function shuffled<T>(items: readonly T[]): T[] {
  const copy = [...items]
  for (let index = copy.length - 1; index > 0; index--) {
    const other = Math.floor(Math.random() * (index + 1))
    ;[copy[index], copy[other]] = [copy[other]!, copy[index]!]
  }
  return copy
}

function ControlButton({
  label,
  active = false,
  disabled = false,
  onClick,
  children,
}: {
  label: string
  active?: boolean
  disabled?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      aria-label={label}
      aria-pressed={active}
      className={`flex size-11 items-center justify-center rounded-pill transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-40 ${
        active ? 'bg-brand-600 text-surface' : 'bg-surface text-ink-secondary hover:bg-brand-50 hover:text-ink'
      }`}
      disabled={disabled}
      onClick={onClick}
      title={label}
      type="button"
    >
      {children}
    </button>
  )
}

/** Browse every card of a deck: flip, step, shuffle, auto-play and full screen. Changes no schedule. */
export function FlashcardViewer({ cards }: { cards: readonly Card[] }) {
  const [order, setOrder] = useState<readonly Card[]>(cards)
  const [isShuffled, setShuffled] = useState(false)
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const [playing, setPlaying] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const root = useRef<HTMLElement>(null)

  // A reloaded deck (card added or removed) restarts from its first card in deck order.
  useEffect(() => {
    setOrder(cards)
    setShuffled(false)
    setIndex(0)
    setFlipped(false)
  }, [cards])

  useEffect(() => {
    const sync = () => setFullscreen(document.fullscreenElement === root.current)
    document.addEventListener('fullscreenchange', sync)
    return () => document.removeEventListener('fullscreenchange', sync)
  }, [])

  const last = order.length - 1
  const go = (next: number) => {
    setIndex(Math.max(0, Math.min(last, next)))
    setFlipped(false)
  }

  useEffect(() => {
    if (!playing) return
    const timer = window.setTimeout(() => {
      if (!flipped) setFlipped(true)
      else if (index < last) go(index + 1)
      else setPlaying(false)
    }, AUTOPLAY_MS)
    return () => window.clearTimeout(timer)
    // `go` only reads index and last, both listed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playing, flipped, index, last])

  const card = order[index]
  if (!card) return null

  return (
    <section
      aria-label="Thẻ ghi nhớ"
      className="flashcard-viewer min-w-0 space-y-4"
      onKeyDown={(event) => {
        if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return
        if (event.key === 'ArrowRight') go(index + 1)
        else if (event.key === 'ArrowLeft') go(index - 1)
        else return
        event.preventDefault()
      }}
      ref={root}
    >
      <FlashcardStage
        back={card.back}
        flipped={flipped}
        front={card.front}
        key={card.id}
        label={`Thẻ ${index + 1} trên ${order.length}, ${flipped ? 'đang xem mặt sau' : 'đang xem mặt trước'}. Bấm để lật`}
        onFlip={() => setFlipped((value) => !value)}
      />
      <div className="grid min-w-0 grid-cols-3 items-center gap-2">
        <p className="hidden text-body-sm text-ink-muted lg:block">Phím ← → để chuyển, Space để lật</p>
        <div className="flex items-center justify-center gap-3">
          <ControlButton disabled={index === 0} label="Thẻ trước" onClick={() => go(index - 1)}>
            <ArrowLeft aria-hidden="true" className="size-5" />
          </ControlButton>
          <span aria-live="polite" className="min-w-16 text-center text-label tabular-nums text-ink">
            {index + 1} / {order.length}
          </span>
          <ControlButton disabled={index === last} label="Thẻ sau" onClick={() => go(index + 1)}>
            <ArrowRight aria-hidden="true" className="size-5" />
          </ControlButton>
        </div>
        <div className="flex items-center justify-end gap-2">
          <ControlButton
            active={playing}
            label={playing ? 'Dừng tự phát' : 'Tự phát'}
            onClick={() => setPlaying((value) => !value)}
          >
            {playing ? <Pause aria-hidden="true" className="size-5" /> : <Play aria-hidden="true" className="size-5" />}
          </ControlButton>
          <ControlButton
            active={isShuffled}
            label={isShuffled ? 'Bỏ trộn thẻ' : 'Trộn thẻ'}
            onClick={() => {
              setOrder(isShuffled ? cards : shuffled(cards))
              setShuffled(!isShuffled)
              setIndex(0)
              setFlipped(false)
            }}
          >
            <Shuffle aria-hidden="true" className="size-5" />
          </ControlButton>
          <ControlButton
            active={fullscreen}
            label={fullscreen ? 'Thoát toàn màn hình' : 'Toàn màn hình'}
            onClick={() => {
              if (document.fullscreenElement) void document.exitFullscreen?.()
              else void root.current?.requestFullscreen?.()
            }}
          >
            {fullscreen ? <Minimize aria-hidden="true" className="size-5" /> : <Maximize aria-hidden="true" className="size-5" />}
          </ControlButton>
        </div>
      </div>
      <div aria-hidden="true" className="h-1 overflow-hidden rounded-pill bg-line">
        <div className="h-full rounded-pill bg-brand-600 transition-all" style={{ width: `${((index + 1) / order.length) * 100}%` }} />
      </div>
    </section>
  )
}
