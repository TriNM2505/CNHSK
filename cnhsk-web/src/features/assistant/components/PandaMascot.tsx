import { cn } from '@/shared/lib/cn'

import './assistant-panda.css'

export type PandaMood = 'idle' | 'thinking'

/**
 * The CNHSK panda drawn in SVG so it can move: it bobs, blinks, swings the tails of its red
 * headband and looks up while the assistant is thinking. Decorative only; screen readers skip it.
 */
export function PandaMascot({
  className,
  mood = 'idle',
}: {
  className?: string
  mood?: PandaMood
}) {
  return (
    <svg
      aria-hidden="true"
      className={cn('cnhsk-panda overflow-visible', className)}
      data-mood={mood}
      focusable="false"
      viewBox="0 0 120 120"
    >
      <g className="cnhsk-panda-body">
        {/* Headband tails, tied behind the right ear. */}
        <g className="cnhsk-panda-tails">
          <path className="fill-brand-700" d="M96 40 L116 30 L111 44 Z" />
          <path className="fill-brand-600" d="M96 42 L118 50 L106 56 Z" />
        </g>
        <g className="cnhsk-panda-ears">
          <circle className="fill-ink" cx="27" cy="28" r="15" />
          <circle className="fill-ink" cx="93" cy="28" r="15" />
          <circle className="fill-brand-100" cx="27" cy="29" r="7" />
          <circle className="fill-brand-100" cx="93" cy="29" r="7" />
        </g>
        <ellipse
          className="fill-surface stroke-ink"
          cx="60"
          cy="64"
          rx="44"
          ry="40"
          strokeWidth="3"
        />
        {/* Red headband across the forehead. */}
        <path
          className="fill-brand-600"
          d="M19 47 C34 36 86 36 101 47 L99 57 C84 47 36 47 21 57 Z"
        />
        <rect className="fill-accent-500" height="4" rx="2" width="18" x="51" y="43" />
        <ellipse
          className="fill-ink"
          cx="41"
          cy="69"
          rx="12"
          ry="14"
          transform="rotate(-18 41 69)"
        />
        <ellipse
          className="fill-ink"
          cx="79"
          cy="69"
          rx="12"
          ry="14"
          transform="rotate(18 79 69)"
        />
        <g className="cnhsk-panda-eyes">
          <circle className="fill-surface" cx="42" cy="68" r="6" />
          <circle className="fill-surface" cx="78" cy="68" r="6" />
          <g className="cnhsk-panda-pupils">
            <circle className="fill-ink" cx="43" cy="69" r="3.2" />
            <circle className="fill-ink" cx="79" cy="69" r="3.2" />
            <circle className="fill-surface" cx="44.2" cy="67.6" r="1.1" />
            <circle className="fill-surface" cx="80.2" cy="67.6" r="1.1" />
          </g>
        </g>
        <ellipse className="fill-brand-100" cx="30" cy="84" rx="7" ry="4.5" />
        <ellipse className="fill-brand-100" cx="90" cy="84" rx="7" ry="4.5" />
        <path className="fill-ink" d="M55 80 Q60 77 65 80 Q60 85 55 80 Z" />
        <path
          className="cnhsk-panda-mouth fill-brand-600 stroke-ink"
          d="M53 87 Q60 96 67 87 Z"
          strokeLinejoin="round"
          strokeWidth="2"
        />
      </g>
    </svg>
  )
}
