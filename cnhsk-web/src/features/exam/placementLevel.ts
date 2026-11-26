import type { PlacementLevel } from './types'

export const placementLabels: Record<PlacementLevel, string> = {
  BELOW_1: 'Chưa tới HSK 1',
  HSK1: 'HSK 1',
  HSK2: 'HSK 2',
  HSK3: 'HSK 3',
  ABOVE_3: 'HSK 3 trở lên',
}

/** The level stored on the profile: below HSK 1 starts at 1, above HSK 3 is recorded as 3 (same as the server). */
export function profileLevel(level: PlacementLevel): 1 | 2 | 3 {
  if (level === 'HSK2') return 2
  if (level === 'HSK3' || level === 'ABOVE_3') return 3
  return 1
}
