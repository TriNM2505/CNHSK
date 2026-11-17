import { useId } from 'react'

export function TranslationLandscapeArt() {
  const landscapeId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      fill="none"
      preserveAspectRatio="xMidYMax slice"
      viewBox="0 0 720 150"
    >
      <defs>
        <linearGradient id={`${landscapeId}-mist`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.15" />
        </linearGradient>
      </defs>
      <path
        d="M0 150V110L72 72L132 104L232 38L336 110L424 64L504 108L610 30L720 92V150Z"
        fill={`url(#${landscapeId}-mist)`}
      />
      <path
        d="M0 132C116 96 180 156 302 128C448 94 522 134 720 104V150H0Z"
        fill="currentColor"
        opacity="0.06"
      />
      <path
        d="M0 149C158 124 260 162 416 142C564 122 642 146 720 132"
        stroke="currentColor"
        strokeWidth="2"
        opacity="0.12"
      />
      <g opacity="0.2" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
        <path d="M532 112L560 124L594 110L580 132H548Z" fill="currentColor" fillOpacity="0.25" />
        <path d="M560 124L562 82L580 114M562 82L542 116L560 124" />
        <path d="M530 138H550M572 141H604M608 133H624" />
        <path
          d="M634 64Q644 54 654 62Q664 54 674 59M488 56Q496 48 504 54Q512 46 521 50"
          strokeWidth="2"
        />
      </g>
    </svg>
  )
}
