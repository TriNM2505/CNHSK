import { bookReference, mediaReference, podcastReference } from '../test-fixtures'
import type { AdminReference, AdminReferencePage } from './types'

export const draftReference: AdminReference = {
  ...mediaReference,
  status: 'DRAFT',
  sortOrder: 0,
  reportCount: 0,
  lastReportedAt: null,
  publishedAt: null,
  updatedAt: '2026-11-14T00:00:00Z',
}
export const publishedReference: AdminReference = {
  ...bookReference,
  status: 'PUBLISHED',
  sortOrder: 1,
  reportCount: 3,
  lastReportedAt: '2026-11-14T01:00:00Z',
  publishedAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-14T00:00:00Z',
}
export const hiddenReference: AdminReference = {
  ...podcastReference,
  status: 'HIDDEN',
  sortOrder: 2,
  reportCount: 1,
  lastReportedAt: '2026-11-14T02:00:00Z',
  publishedAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-14T00:00:00Z',
}
export const adminPage: AdminReferencePage = {
  items: [draftReference, publishedReference, hiddenReference],
  page: 0,
  size: 20,
  total: 3,
}
