import { apiRequest } from '@/shared/api'

import type { AdminTopic, AdminTopicPage, TopicFilters } from '../types'

export function listAdminTopics(
  { q = '', hsk, page = 0, size = 20 }: Partial<TopicFilters> & { size?: number } = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (q.trim()) query.set('q', q.trim())
  if (hsk !== undefined) query.set('hsk', String(hsk))
  return apiRequest<AdminTopicPage>(`/api/admin/topics?${query}`, { signal })
}

/** Read the unfiltered catalog only for resolving a cycle or reconciling stale data. */
export async function readAdminTopicCatalog(signal?: AbortSignal) {
  const topics: AdminTopic[] = []
  let page = 0
  let response: AdminTopicPage
  do {
    response = await listAdminTopics({ page, size: 50 }, signal)
    topics.push(...response.items)
    page += 1
  } while (response.items.length > 0 && page * response.size < response.totalElements)
  return topics
}

export function setTopicPrerequisites(id: number, prereqIds: readonly number[]) {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Mã chủ đề không hợp lệ.')
  return apiRequest<AdminTopic>(`/api/admin/topics/${id}/prerequisites`, {
    method: 'PUT',
    body: { prereqIds },
  })
}
