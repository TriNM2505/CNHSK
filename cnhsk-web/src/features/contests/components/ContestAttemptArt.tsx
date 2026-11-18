import { useId } from 'react'

export function ContestAttemptMountains({ position }: { position: 'top' | 'bottom' }) {
  const gradientId = useId()
  return (
    <svg
      aria-hidden="true"
      className={`pointer-events-none absolute right-0 text-brand-600 ${position === 'top' ? 'top-0 h-14 w-2/3' : 'inset-x-0 bottom-0 h-20 w-full'}`}
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 640 100"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.22" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.01" />
        </linearGradient>
      </defs>
      <path
        d="M84 100 C134 79 166 58 188 63 L243 83 C283 50 316 11 339 21 L394 56 C433 17 461 0 479 12 L552 63 C580 30 600 37 640 69 L640 100 Z"
        fill={`url(#${gradientId})`}
      />
      <path
        d="M0 100 C78 82 106 64 136 74 L230 95 C288 65 316 52 342 62 L413 83 C456 59 487 53 521 67 L640 100 Z"
        fill={`url(#${gradientId})`}
        opacity="0.7"
      />
      <path
        d="M222 100 C282 81 313 80 345 87 L416 98 C455 81 482 73 512 80 L584 97 C605 88 623 90 640 96 L640 100 Z"
        fill="currentColor"
        opacity="0.07"
      />
    </svg>
  )
}
