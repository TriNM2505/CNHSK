import { cn } from '@/shared/lib/cn'

export type HskLevel = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9

export interface LevelBadgeProps {
  className?: string
  exact?: boolean
  level: HskLevel
}

function getLevelClasses(level: HskLevel) {
  if (level <= 2) {
    return 'border-success bg-success-bg text-success'
  }
  if (level <= 4) {
    return 'border-info bg-info-bg text-info'
  }
  if (level <= 6) {
    return 'border-warning bg-warning-bg text-warning'
  }
  return 'border-brand-100 bg-brand-50 text-brand-700'
}

export function LevelBadge({ className, exact = false, level }: LevelBadgeProps) {
  const label = level >= 7 && !exact ? 'HSK 7–9' : `HSK ${level}`

  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-sm border px-2 py-0.5 text-caption font-bold',
        getLevelClasses(level),
        className,
      )}
    >
      {label}
    </span>
  )
}
