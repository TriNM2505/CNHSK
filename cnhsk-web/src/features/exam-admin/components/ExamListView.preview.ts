import type { ExamListViewProps } from './ExamListView'
import { pageFixture, summaryFixture } from '../test-fixtures'
const noop = () => undefined
const data = {
  ...pageFixture,
  items: [
    summaryFixture,
    {
      ...summaryFixture,
      id: 2302,
      code: 'DA-CONG-BO',
      title: 'Chủ đề trường học 学校',
      status: 'PUBLISHED' as const,
      publishedAt: '2026-11-15T08:00:00Z',
      inUse: true,
    },
    { ...summaryFixture, id: 2303, code: 'DA-LUU-TRU', status: 'ARCHIVED' as const },
  ],
  totalElements: 3,
}
export const listPreviews: Record<string, ExamListViewProps> = {
  ready: { state: 'ready', data, filters: {}, onFilter: noop, onRetry: noop, onAction: noop },
  loading: { state: 'loading', filters: {}, onFilter: noop, onRetry: noop, onAction: noop },
  empty: {
    state: 'empty',
    data: { ...data, items: [], totalElements: 0 },
    filters: {},
    onFilter: noop,
    onRetry: noop,
    onAction: noop,
  },
  error: {
    state: 'error',
    filters: {},
    error: 'Không tải được đề thi. Kiểm tra kết nối và thử lại.',
    onFilter: noop,
    onRetry: noop,
    onAction: noop,
  },
}
