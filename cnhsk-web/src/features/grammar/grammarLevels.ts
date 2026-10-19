import type { GrammarSummary } from './types'

export type GrammarLevelTab = '1' | '2' | '3' | '4' | '5' | '6' | '7-9'

export const GRAMMAR_LEVEL_TABS: readonly GrammarLevelTab[] = ['1', '2', '3', '4', '5', '6', '7-9']

export function isGrammarLevelTab(value: string | null): value is GrammarLevelTab {
  return GRAMMAR_LEVEL_TABS.some((level) => level === value)
}

export function grammarTabForLevel(level: number): GrammarLevelTab {
  return level >= 7 ? '7-9' : (String(level) as GrammarLevelTab)
}

export function groupGrammarPoints(points: readonly GrammarSummary[]) {
  const groups: Record<GrammarLevelTab, GrammarSummary[]> = {
    '1': [],
    '2': [],
    '3': [],
    '4': [],
    '5': [],
    '6': [],
    '7-9': [],
  }

  points.forEach((point) => groups[grammarTabForLevel(point.hskLevel)].push(point))
  return groups
}

export function defaultGrammarTab(points: readonly GrammarSummary[]): GrammarLevelTab {
  const groups = groupGrammarPoints(points)
  return GRAMMAR_LEVEL_TABS.find((level) => groups[level].length > 0) ?? '1'
}

export function grammarTabLabel(level: GrammarLevelTab) {
  return `HSK ${level === '7-9' ? '7–9' : level}`
}
