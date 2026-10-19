import type { TopicListViewProps } from './TopicListView'
import type { TopicSummary } from '../types'

const topics: TopicSummary[] = [
  {
    bestTestPercent: 88,
    code: 'FAMILY',
    hskLevel: 1,
    id: 1,
    learnedWords: 8,
    name: 'Gia đình 家庭',
    percent: 80,
    status: 'IN_PROGRESS',
    totalWords: 10,
  },
  {
    bestTestPercent: 96,
    code: 'GREETINGS',
    hskLevel: 1,
    id: 2,
    learnedWords: 12,
    name: 'Chào hỏi 问候',
    percent: 100,
    status: 'COMPLETED',
    totalWords: 12,
  },
  {
    bestTestPercent: null,
    code: 'SCHOOL',
    hskLevel: 2,
    id: 3,
    learnedWords: 0,
    name: 'Trường học 学校',
    percent: 0,
    status: 'NOT_STARTED',
    totalWords: 15,
  },
]

const noop = () => undefined

const base: TopicListViewProps = {
  onClearClientFilters: noop,
  onHskLevelChange: noop,
  onRetry: noop,
  onSearchChange: noop,
  onStatusChange: noop,
  search: '',
  state: 'ready',
  stats: { completed: 1, inProgress: 1, total: 3 },
  status: 'ALL',
  topics,
}

export const topicListPreviews = {
  empty: { ...base, state: 'empty', stats: { completed: 0, inProgress: 0, total: 0 }, topics: [] },
  error: {
    ...base,
    error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'req-preview' },
    state: 'error',
    topics: [],
  },
  filteredEmpty: { ...base, search: 'không có', topics: [] },
  loading: { ...base, state: 'loading', topics: [] },
  ready: base,
} satisfies Record<string, TopicListViewProps>
