import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'

import { getGrammarPoints } from '../api/grammar'
import { GrammarListView } from '../components/GrammarListView'
import {
  defaultGrammarTab,
  groupGrammarPoints,
  isGrammarLevelTab,
  type GrammarLevelTab,
} from '../grammarLevels'

export default function Scr017GrammarListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = useApiQuery((signal) => getGrammarPoints(undefined, signal), [], {
    isEmpty: (points) => points.length === 0,
  })
  const points = query.data ?? []
  const groups = groupGrammarPoints(points)
  const rawLevel = searchParams.get('level')
  const selectedLevel: GrammarLevelTab = isGrammarLevelTab(rawLevel)
    ? rawLevel
    : defaultGrammarTab(points)

  useEffect(() => {
    if (!query.data || rawLevel === selectedLevel) return
    const normalized = new URLSearchParams(searchParams)
    normalized.set('level', selectedLevel)
    setSearchParams(normalized, { replace: true })
  }, [query.data, rawLevel, searchParams, selectedLevel, setSearchParams])

  const changeLevel = (level: GrammarLevelTab) => {
    const next = new URLSearchParams(searchParams)
    next.set('level', level)
    setSearchParams(next)
  }

  return (
    <GrammarListView
      error={query.error}
      groups={groups}
      onLevelChange={changeLevel}
      onRetry={query.reload}
      selectedLevel={selectedLevel}
      state={query.state}
    />
  )
}
