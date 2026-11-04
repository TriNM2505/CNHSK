import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getTargetedNodeName, startTargetedPractice } from './targetedPractice'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('targeted practice API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue(undefined))

  it('FR001_targetedPractice: gửi KID, dạng câu và câu cần loại trừ', async () => {
    await startTargetedPractice({
      excludeQuestionId: 48,
      kid: 'NP1-101',
      qtype: 'MULTIPLE_CHOICE',
    })

    expect(apiRequestMock).toHaveBeenCalledWith('/api/targeted-practice/sessions', {
      body: { excludeQuestionId: 48, kid: 'NP1-101', qtype: 'MULTIPLE_CHOICE' },
      method: 'POST',
    })
  })

  it('FR027_AT28: mã hóa KID trong URL và lấy tên tiếng Việt', async () => {
    const signal = new AbortController().signal
    apiRequestMock.mockResolvedValue({ nameVi: 'Mẫu câu cơ bản' })

    await expect(getTargetedNodeName('NP1/101', signal)).resolves.toBe('Mẫu câu cơ bản')
    expect(apiRequestMock).toHaveBeenCalledWith('/api/knowledge-tree/nodes/NP1%2F101', { signal })
  })
})
