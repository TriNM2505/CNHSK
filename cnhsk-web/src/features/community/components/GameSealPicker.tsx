import type { GameOption } from '@/features/leaderboard/types'
import { cn } from '@/shared/lib/cn'

import { gameSeal } from '../game-seals'

export type GameSealPickerProps = {
  disabled?: boolean
  games: readonly GameOption[]
  /** Adds a first "Tổng điểm" tile whose code is the empty string. */
  includeTotal?: boolean
  label?: string
  onChange: (game: string) => void
  value: string
}

/** Games as stamp tiles; the chosen tile turns dark with a gold seal. */
export function GameSealPicker({
  disabled = false,
  games,
  includeTotal = false,
  label = 'Trò chơi',
  onChange,
  value,
}: GameSealPickerProps) {
  const options = includeTotal ? [{ code: '', name: 'Tổng điểm' }, ...games] : games
  return (
    <div aria-label={label} className="grid grid-cols-4 gap-1.5" role="radiogroup">
      {options.map((option) => {
        const selected = value === option.code
        return (
          <button
            aria-checked={selected}
            className={cn(
              'group flex min-w-0 flex-col items-center gap-1 rounded-md px-0.5 pb-1.5 pt-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-60',
              selected ? 'bg-brand-800 text-surface shadow-md' : 'bg-canvas text-ink hover:bg-brand-50',
            )}
            disabled={disabled}
            key={option.code || 'all'}
            onClick={() => onChange(option.code)}
            role="radio"
            title={option.name}
            type="button"
          >
            <span
              aria-hidden="true"
              className={cn(
                'flex size-9 items-center justify-center rounded-sm border font-hanzi text-h3 leading-none transition-transform group-hover:-rotate-3',
                selected
                  ? 'border-accent-500 bg-brand-700 text-accent-500'
                  : 'border-brand-100 bg-surface text-brand-700',
              )}
              lang="zh-CN"
            >
              {gameSeal(option.code, option.name)}
            </span>
            <span className="line-clamp-2 w-full text-center text-caption leading-tight">{option.name}</span>
          </button>
        )
      })}
    </div>
  )
}
