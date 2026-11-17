import { apiRequest, invalidResponseError } from '@/shared/api'
import type { RunPage, RunStatus } from '../types'

export function listAiRuns(
  { status, page = 0, size = 20 }: { status?: RunStatus; page?: number; size?: number } = {},
  signal?: AbortSignal,
): Promise<RunPage> {
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 50)
    throw new RangeError('Invalid pagination')
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return apiRequest<RunPage>(`/api/admin/ai-generation-runs?${query}`, { signal })
}

export async function triggerAiRun(signal?: AbortSignal): Promise<{ started: true }> {
  const result = await apiRequest<{ started: boolean }>('/api/admin/ai-generation-runs/trigger', {
    method: 'POST',
    signal,
  })
  if (result?.started !== true) throw invalidResponseError()
  return { started: true }
}
