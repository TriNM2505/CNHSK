import { RotateCw } from 'lucide-react'

import { DeckDetailText } from './DeckDetailParts'

export interface FlashcardStageProps {
  front: string
  back: string
  flipped: boolean
  onFlip: () => void
  /** Accessible name of the card button. */
  label: string
  /** Review keeps the answer out of the page until the learner flips; browsing may preload it. */
  hideBackUntilFlipped?: boolean
  disabled?: boolean
}

/** One big two-sided card that turns over in 3D when pressed (Space or Enter on keyboard). */
export function FlashcardStage({
  front,
  back,
  flipped,
  onFlip,
  label,
  hideBackUntilFlipped = false,
  disabled = false,
}: FlashcardStageProps) {
  const face =
    'flashcard-face absolute inset-0 flex min-w-0 flex-col rounded-lg border border-line bg-surface p-5 shadow-md sm:p-8'
  const text = (value: string) => (
    <p
      className={`m-auto max-h-full w-full overflow-y-auto whitespace-pre-wrap break-words text-center text-ink ${
        // Short terms read large like a flashcard; long explanations drop to a readable size.
        value.length > 120 ? 'text-body' : value.length > 40 ? 'text-h2' : /\p{Script=Han}/u.test(value) ? 'text-hanzi-lg' : 'text-h1'
      }`}
    >
      <DeckDetailText text={value} />
    </p>
  )
  return (
    <button
      aria-label={label}
      className="flashcard-stage group relative block h-72 w-full min-w-0 rounded-lg text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:h-96"
      data-flipped={flipped}
      disabled={disabled}
      onClick={onFlip}
      type="button"
    >
      <span className="flashcard-inner relative block h-full w-full">
        <span aria-hidden={flipped} className={`${face} flashcard-front`}>
          <span className="flex items-center justify-between text-label text-ink-muted">
            Mặt trước
            <span className="inline-flex items-center gap-1 text-body-sm opacity-0 transition-opacity group-hover:opacity-100">
              <RotateCw aria-hidden="true" className="size-4" />
              Bấm để lật
            </span>
          </span>
          {text(front)}
        </span>
        <span aria-hidden={!flipped} className={`${face} flashcard-back`}>
          <span className="text-label text-brand-700">Mặt sau</span>
          {hideBackUntilFlipped && !flipped ? null : text(back)}
        </span>
      </span>
    </button>
  )
}
