import { useId } from 'react'

export function QuizLandscapeArt({ bridge = false }: { bridge?: boolean }) {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 600 280"
    >
      <defs>
        <linearGradient id={`${sceneId}-hills`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>
      <circle cx="500" cy="55" r="26" fill="currentColor" opacity="0.04" />
      <path
        d="M0 280V242L70 214L148 246L258 164L362 246L452 192L528 234L600 178V280Z"
        fill={`url(#${sceneId}-hills)`}
      />
      <path
        d="M0 278C110 240 184 286 300 260C418 234 490 280 600 252V280H0Z"
        fill="currentColor"
        opacity="0.05"
      />
      {bridge ? (
        <g opacity="0.12" stroke="currentColor" strokeLinecap="round">
          <path d="M360 258Q450 176 540 258M360 252Q450 170 540 252" strokeWidth="2" />
          <path d="M380 238V230M402 224V216M426 214V206M450 210V202M474 214V206M498 224V216M520 238V230M396 258Q450 220 504 258" />
        </g>
      ) : (
        <g fill="currentColor" opacity="0.08">
          <path d="M466 218C450 222 438 236 430 252C448 246 460 232 466 218ZM472 208C486 212 500 228 506 244C488 238 478 224 472 208Z" />
          <path d="M464 270Q478 236 468 196" stroke="currentColor" strokeWidth="2" />
        </g>
      )}
    </svg>
  )
}
