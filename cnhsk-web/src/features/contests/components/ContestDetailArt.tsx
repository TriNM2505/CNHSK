export function ContestBambooArt({ position = 'top' }: { position?: 'top' | 'bottom' }) {
  return (
    <svg
      aria-hidden="true"
      className={`pointer-events-none absolute right-0 text-brand-600 ${position === 'top' ? 'top-0 h-20 w-20 opacity-30' : 'bottom-0 h-20 w-24 opacity-40'}`}
      fill="currentColor"
      viewBox="0 0 140 120"
    >
      <path d="M130 126 L96 3 M148 126 L120 0" fill="none" stroke="currentColor" strokeWidth="5" />
      <path
        d="M100 24 L106 22 M111 62 L117 60 M122 100 L129 98 M124 24 L131 23 M132 64 L138 63"
        className="stroke-surface"
        fill="none"
        strokeWidth="1.8"
      />
      <path
        d="M113 12 Q75 7 30 32 M109 38 Q69 30 36 54"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.4"
      />
      <path d="M48 23 Q23 24 6 38 Q31 33 48 23 Z" />
      <path d="M62 18 Q43 35 36 52 Q54 38 62 18 Z" />
      <path d="M80 14 Q66 35 65 53 Q79 35 80 14 Z" />
      <path d="M96 14 Q87 36 93 55 Q101 34 96 14 Z" />
      <path d="M52 46 Q27 49 13 65 Q35 58 52 46 Z" />
      <path d="M70 39 Q51 59 47 78 Q63 61 70 39 Z" />
      <path d="M88 37 Q78 59 83 77 Q93 55 88 37 Z" />
      <path d="M103 40 Q102 63 113 80 Q113 57 103 40 Z" />
    </svg>
  )
}

export function ContestPrizeBackdrop() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 h-full w-full text-brand-600"
      fill="currentColor"
      preserveAspectRatio="none"
      viewBox="0 0 680 220"
    >
      <path d="M364 49 L452 16 L496 36 L553 9 L626 37 L680 17 L680 61 L364 61 Z" opacity="0.07" />
      <path d="M401 52 L494 27 L538 43 L593 17 L680 50 L680 66 L401 66 Z" opacity="0.05" />
      <path
        d="M315 220 C392 164 416 186 447 197 C488 158 529 181 558 191 C610 158 641 180 680 190 L680 220 Z"
        opacity="0.1"
      />
      <path
        d="M404 220 C448 190 482 185 519 202 C554 175 582 183 615 200 C645 184 663 187 680 199 L680 220 Z"
        opacity="0.12"
      />
    </svg>
  )
}

export function ContestPrizeMedal({ firstPlace }: { firstPlace: boolean }) {
  return (
    <svg aria-hidden="true" className="size-12 shrink-0" fill="none" viewBox="0 0 64 64">
      <path
        d="M19 37 L13 61 L25 55 L31 62 L34 39 Z"
        className={firstPlace ? 'fill-brand-600' : 'fill-ink-muted'}
      />
      <path
        d="M34 38 L34 61 L42 54 L53 59 L47 36 Z"
        className={firstPlace ? 'fill-brand-700' : 'fill-ink-secondary'}
      />
      <circle
        cx="33"
        cy="27"
        r="23"
        className={firstPlace ? 'fill-warning-bg stroke-accent-500' : 'fill-canvas stroke-line'}
        strokeWidth="2"
      />
      <circle
        cx="33"
        cy="27"
        r="18"
        className={firstPlace ? 'fill-accent-500 stroke-warning' : 'fill-line stroke-ink-muted'}
        strokeWidth="1.5"
      />
      <circle
        cx="33"
        cy="27"
        r="14.5"
        className={firstPlace ? 'stroke-warning-bg' : 'stroke-surface'}
      />
      <path
        d="M33 15 L36.4 22 L44 23.1 L38.5 28.5 L39.8 36.1 L33 32.5 L26.2 36.1 L27.5 28.5 L22 23.1 L29.6 22 Z"
        className={firstPlace ? 'fill-warning-bg stroke-warning' : 'fill-surface stroke-ink-muted'}
        strokeLinejoin="round"
      />
    </svg>
  )
}
