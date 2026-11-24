import { cn } from '@/shared/lib/cn'

import { SUPPORTED_HSK_LEVELS } from './LevelBadge'

/** HSK 1 · 2 · 3 as one row of buttons; pair it with `useHskLevel` to keep the level in the address. */
export function HskLevelPicker({
  className,
  level,
  onChange,
}: {
  className?: string
  level: number
  onChange: (level: number) => void
}) {
  return (
    <div
      aria-label="Cấp HSK"
      className={cn('inline-flex rounded-pill border border-line bg-surface p-1', className)}
      role="group"
    >
      {SUPPORTED_HSK_LEVELS.map((value) => (
        <button
          aria-pressed={level === value}
          className={cn(
            'min-h-10 rounded-pill px-4 text-body-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
            level === value ? 'bg-brand-600 text-surface' : 'text-ink-secondary hover:text-ink',
          )}
          key={value}
          onClick={() => onChange(value)}
          type="button"
        >
          HSK {value}
        </button>
      ))}
    </div>
  )
}
