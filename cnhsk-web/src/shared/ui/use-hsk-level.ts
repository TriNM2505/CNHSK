import { useSearchParams } from 'react-router-dom'

import { SUPPORTED_HSK_LEVELS } from './LevelBadge'

function supported(value: number | null | undefined): number | undefined {
  return value !== null &&
    value !== undefined &&
    (SUPPORTED_HSK_LEVELS as readonly number[]).includes(value)
    ? value
    : undefined
}

/**
 * The HSK level a learning page shows: `?hsk=` when the learner picked one, otherwise the account's level,
 * otherwise HSK 1. Picking a level writes it to the address so a shared link opens the same level.
 */
export function useHskLevel(accountLevel?: number | null): [number, (level: number) => void] {
  const [params, setParams] = useSearchParams()
  const level = supported(Number(params.get('hsk'))) ?? supported(accountLevel) ?? 1
  function choose(next: number) {
    const updated = new URLSearchParams(params)
    updated.set('hsk', String(next))
    setParams(updated, { replace: true })
  }
  return [level, choose]
}
