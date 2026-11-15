import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/shared/api'
import {
  getAiUsage,
  getSettingHistory,
  listSettings,
  resetSetting,
  updateSetting,
} from './settings'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)
beforeEach(() => {
  request.mockReset()
})
describe('settings HTTP contract', () => {
  it('lists settings and reads ADMIN usage independently with cancellation', async () => {
    const signal = new AbortController().signal
    await listSettings(signal)
    await getAiUsage(signal)
    expect(request).toHaveBeenNthCalledWith(1, '/api/admin/settings', { signal })
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/ai-usage', { signal })
  })
  it('uses PUT and DELETE JSON bodies through the shared cookie/CSRF client', async () => {
    await updateSetting('assistant.daily-limit', 0, 'Dừng tạm thời.')
    await resetSetting('assistant.daily-limit', 'Về chuẩn.')
    expect(request).toHaveBeenNthCalledWith(1, '/api/admin/settings/assistant.daily-limit', {
      method: 'PUT',
      body: { value: 0, reason: 'Dừng tạm thời.' },
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/admin/settings/assistant.daily-limit', {
      method: 'DELETE',
      body: { reason: 'Về chuẩn.' },
    })
  })
  it('encodes a key as a path segment and requests zero-based history pages', async () => {
    const signal = new AbortController().signal
    await getSettingHistory('test/a?b', 2, signal)
    expect(request).toHaveBeenCalledWith(
      '/api/admin/settings/test%2Fa%3Fb/history?page=2&size=20',
      { signal },
    )
  })
  it.each(['edit', 'reset'] as const)('does not retry a failed %s mutation', async (mode) => {
    request.mockRejectedValue(new Error('Network'))
    await expect(
      mode === 'edit' ? updateSetting('test', 1, 'Lý do') : resetSetting('test', 'Lý do'),
    ).rejects.toThrow('Network')
    expect(request).toHaveBeenCalledTimes(1)
  })
})
