import { useId } from 'react'

export function GrammarLessonLandscape({ river = false }: { river?: boolean }) {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-24 w-full select-none text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 1000 180"
    >
      <defs>
        <linearGradient id={`${sceneId}-mist`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="55%" stopColor="currentColor" stopOpacity="0.03" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>
      <path
        d={
          river
            ? 'M0 138C120 60 210 122 312 94C418 64 498 132 610 86C734 36 826 116 1000 58V180H0Z'
            : 'M0 132L112 62L194 114L328 24L440 110L550 52L660 122L788 46L894 112L1000 76V180H0Z'
        }
        fill={`url(#${sceneId}-mist)`}
      />
      <path
        d="M0 172C112 122 210 162 318 130C438 96 504 168 632 132C754 96 860 160 1000 110V180H0Z"
        fill="currentColor"
        opacity="0.04"
      />
      <path
        d="M0 178C154 140 276 188 422 158C576 126 682 178 826 154C900 142 956 152 1000 148V180H0Z"
        fill="currentColor"
        opacity="0.05"
      />
      {river ? (
        <path
          d="M1000 125C840 114 718 164 564 148C408 132 336 172 168 176"
          opacity="0.08"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="3"
        />
      ) : null}
    </svg>
  )
}
