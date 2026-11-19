import { useId } from 'react'
import { HanziText } from '@/shared/ui'

export function TopicText({ text }: { text: string }) {
  return text.split(/([\p{Script=Han}\u3000-\u303f\uff00-\uffef]+)/u).map((part, index) =>
    /[\p{Script=Han}\u3000-\u303f\uff00-\uffef]/u.test(part) ? (
      <HanziText key={index} size="sm" className="topic-text">
        {part}
      </HanziText>
    ) : (
      part
    ),
  )
}

export function TopicLandscapeArt() {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      viewBox="0 0 600 280"
      preserveAspectRatio="none"
      fill="none"
    >
      <defs>
        <linearGradient id={`${sceneId}-hills`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>
      <circle cx="516" cy="42" r="25" fill="currentColor" opacity="0.04" />
      <path
        d="M0 280V250L80 230L150 252L248 208L350 246L458 180L540 236L600 208V280Z"
        fill={`url(#${sceneId}-hills)`}
      />
      <path
        d="M0 280C104 242 160 282 280 256C400 232 486 278 600 248V280Z"
        fill="currentColor"
        opacity="0.05"
      />
      <g stroke="currentColor" opacity="0.12" strokeLinecap="round">
        <path d="M376 256H438V226H510V196H568" strokeWidth="2" />
        <circle cx="376" cy="256" r="4" fill="currentColor" />
        <circle cx="438" cy="226" r="4" fill="currentColor" />
        <circle cx="510" cy="196" r="4" fill="currentColor" />
      </g>
    </svg>
  )
}
