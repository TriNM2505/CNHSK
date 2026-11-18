export function ContestRankingMedal({ rank }: { rank: number }) {
  if (rank > 3) {
    return (
      <span className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-50 font-bold text-brand-700">
        {rank}
      </span>
    )
  }

  const tones =
    rank === 1
      ? {
          ribbon: 'fill-brand-600',
          edge: 'fill-warning-bg stroke-accent-500',
          face: 'fill-accent-500 stroke-warning',
          text: 'fill-warning',
        }
      : rank === 2
        ? {
            ribbon: 'fill-ink-secondary',
            edge: 'fill-canvas stroke-ink-muted',
            face: 'fill-line stroke-ink-muted',
            text: 'fill-ink-secondary',
          }
        : {
            ribbon: 'fill-warning',
            edge: 'fill-warning-bg stroke-warning',
            face: 'fill-warning/60 stroke-warning',
            text: 'fill-brand-800',
          }

  return (
    <span className="inline-flex size-10 shrink-0 items-center justify-center">
      <span className="sr-only">{rank}</span>
      <svg aria-hidden="true" className="size-10" fill="none" viewBox="0 0 56 64">
        <path
          className={tones.ribbon}
          d="M15 36 L8 61 L20 56 L26 64 L31 38 Z M31 37 L30 62 L38 55 L50 60 L43 36 Z"
        />
        <circle className={tones.edge} cx="29" cy="26" r="23" strokeWidth="2" />
        <circle className={tones.face} cx="29" cy="26" r="18.5" strokeWidth="1" />
        <path
          className="stroke-surface"
          d="M14 23 A15 15 0 0 1 29 11"
          opacity="0.6"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <text
          className={`${tones.text} font-sans font-bold`}
          fontSize="23"
          textAnchor="middle"
          x="29"
          y="34"
        >
          {rank}
        </text>
      </svg>
    </span>
  )
}

export function ContestRankingBackdrop() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute bottom-0 right-0 h-14 w-1/2 text-brand-600"
      fill="currentColor"
      preserveAspectRatio="none"
      viewBox="0 0 600 100"
    >
      <path
        d="M0 100 L123 54 Q133 48 145 55 L223 78 L314 34 Q326 29 338 36 L412 66 L491 16 Q505 9 517 18 L600 62 L600 100 Z"
        opacity="0.1"
      />
      <path
        d="M104 100 L197 70 Q207 64 222 71 L300 92 L393 56 Q407 50 423 60 L477 80 L532 52 Q544 46 557 54 L600 81 L600 100 Z"
        opacity="0.08"
      />
    </svg>
  )
}
