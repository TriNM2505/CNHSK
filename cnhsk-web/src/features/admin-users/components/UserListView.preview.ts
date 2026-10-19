import type { AdminUserPage } from '../types'
import type { UserListViewProps } from './UserListView'

const readyData: AdminUserPage = {
  items: [
    {
      createdAt: '2026-10-19T17:30:00Z',
      displayName: 'Minh Anh',
      emailMasked: 'mi•••@example.com',
      id: 101,
      roles: ['LEARNER'],
      status: 'ACTIVE',
    },
    {
      createdAt: '2026-10-18T08:00:00Z',
      displayName: 'Thanh Hà',
      emailMasked: 'th•••@example.com',
      id: 102,
      roles: ['TEACHER', 'MODERATOR'],
      status: 'UNVERIFIED',
    },
    {
      createdAt: '2026-10-17T03:00:00Z',
      displayName: 'Quang Huy',
      emailMasked: 'qu•••@example.com',
      id: 103,
      roles: ['CONTENT_EDITOR', 'ADMIN'],
      status: 'BANNED',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 3,
}

const base = {
  onClearFilters: () => undefined,
  onFirstPage: () => undefined,
  onOpenUser: () => undefined,
  onPageChange: () => undefined,
  onQueryChange: () => undefined,
  onRetry: () => undefined,
  onRoleChange: () => undefined,
  onStatusChange: () => undefined,
  query: { page: 0, q: '', role: '', status: '' },
  queryDraft: '',
  state: 'ready',
} satisfies UserListViewProps

export const userListPreviews = {
  ready: { ...base, data: readyData },
  loading: { ...base, state: 'loading' },
  empty: {
    ...base,
    data: { items: [], page: 0, size: 20, totalElements: 0 },
    state: 'empty',
  },
  filteredEmpty: {
    ...base,
    data: { items: [], page: 0, size: 20, totalElements: 0 },
    query: { ...base.query, q: 'không có' },
    queryDraft: 'không có',
    state: 'empty',
  },
  outOfRange: {
    ...base,
    data: { items: [], page: 99, size: 20, totalElements: 3 },
    query: { ...base.query, page: 99 },
    state: 'empty',
  },
  error: {
    ...base,
    error: { message: 'Không tải được danh sách tài khoản.' },
    state: 'error',
  },
} satisfies Record<string, UserListViewProps>
