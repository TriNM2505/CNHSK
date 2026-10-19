import { groupGrammarPoints } from '../grammarLevels'
import type { GrammarSummary } from '../types'
import type { GrammarListViewProps } from './GrammarListView'

const points: GrammarSummary[] = [
  {
    bestPercent: 80,
    exerciseCount: 4,
    hskLevel: 1,
    id: 11,
    learned: true,
    text: '是……的',
    titleVi: 'Nhấn mạnh thời gian hoặc cách thức',
  },
  {
    bestPercent: null,
    exerciseCount: 3,
    hskLevel: 2,
    id: 12,
    learned: false,
    text: '虽然……但是……',
    titleVi: 'Quan hệ nhượng bộ',
  },
  {
    bestPercent: 100,
    exerciseCount: 5,
    hskLevel: 7,
    id: 13,
    learned: true,
    text: '与其……不如……',
    titleVi: 'So sánh lựa chọn',
  },
]

const noop = () => undefined
const groups = groupGrammarPoints(points)

const base: GrammarListViewProps = {
  groups,
  onLevelChange: noop,
  onRetry: noop,
  selectedLevel: '1',
  state: 'ready',
}

export const grammarListPreviews = {
  empty: { ...base, groups: groupGrammarPoints([]), state: 'empty' },
  error: {
    ...base,
    error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'req-preview' },
    state: 'error',
  },
  loading: { ...base, groups: groupGrammarPoints([]), state: 'loading' },
  ready: base,
} satisfies Record<string, GrammarListViewProps>
