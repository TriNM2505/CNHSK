import { apiRequest } from '@/shared/api'

import type { KnowledgeNodeDetail, KnowledgeTree, KnowledgeTreeParams } from '../types'

const knowledgePath = '/api/knowledge-tree'

export function getKnowledgeTree(
  { domain, hskLevel }: KnowledgeTreeParams = {},
  signal?: AbortSignal,
): Promise<KnowledgeTree> {
  const query = new URLSearchParams()
  if (domain !== undefined) query.set('domain', domain)
  if (hskLevel !== undefined) query.set('hskLevel', String(hskLevel))
  const suffix = query.size ? `?${query.toString()}` : ''
  return apiRequest<KnowledgeTree>(`${knowledgePath}${suffix}`, { signal })
}

export function getKnowledgeNode(kid: string, signal?: AbortSignal): Promise<KnowledgeNodeDetail> {
  return apiRequest<KnowledgeNodeDetail>(`${knowledgePath}/nodes/${encodeURIComponent(kid)}`, {
    signal,
  })
}
