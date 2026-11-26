import { useState } from 'react'

import { hskRangeLabel } from '../referenceLabels'
import type { PublicReference } from '../types'

/** Cover colour per starting HSK level, so a shelf reads by level at a glance. */
const tone: Record<number, string> = {
  1: 'bg-brand-600 text-surface border-brand-800',
  2: 'bg-brand-800 text-surface border-brand-700',
  3: 'bg-accent-500 text-ink border-accent-700',
}

type Book = Pick<PublicReference, 'title' | 'thumbnailUrl' | 'hskMin' | 'hskMax' | 'publisher'>

/**
 * A book cover. The publisher's cover image is only linked, never stored; when there is none (or it
 * fails to load) the cover is drawn from the title and level, so the catalogue needs no file storage.
 */
export function BookCover({ book, large = false }: { book: Book; large?: boolean }) {
  const [broken, setBroken] = useState(false)
  const frame = 'relative aspect-[3/4] w-full overflow-hidden rounded-sm shadow-md'
  if (book.thumbnailUrl && !broken) {
    return (
      <div className={`${frame} bg-surface-subtle`}>
        <img
          alt=""
          className="size-full object-cover"
          decoding="async"
          loading={large ? 'eager' : 'lazy'}
          onError={() => setBroken(true)}
          referrerPolicy="no-referrer"
          src={book.thumbnailUrl}
        />
        <span aria-hidden="true" className="absolute inset-y-0 left-0 w-2 bg-ink/20" />
      </div>
    )
  }
  const colours = tone[book.hskMin ?? 0] ?? 'bg-ink text-surface border-ink-secondary'
  return (
    <div
      aria-hidden="true"
      className={`${frame} flex flex-col border-l-[10px] p-3 ${colours} ${large ? 'sm:p-6' : ''}`}
    >
      <span
        className={`pointer-events-none absolute -bottom-6 -right-3 select-none font-hanzi leading-none opacity-15 ${
          large ? 'text-[11rem]' : 'text-[7rem]'
        }`}
      >
        书
      </span>
      <span className="text-caption uppercase tracking-wider opacity-90">
        {hskRangeLabel(book.hskMin, book.hskMax)}
      </span>
      <span
        className={`mt-auto line-clamp-4 break-words font-bold ${large ? 'text-h2' : 'text-label'}`}
      >
        {book.title}
      </span>
      <span className="mt-2 line-clamp-1 border-t border-surface/40 pt-2 text-caption opacity-90">
        {book.publisher ?? 'Sách tham khảo HSK'}
      </span>
    </div>
  )
}
