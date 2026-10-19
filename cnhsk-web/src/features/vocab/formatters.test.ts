import { describe, expect, it } from 'vitest'

import { formatTopicDateTime } from './formatters'

describe('topic formatters', () => {
  it('định dạng thời điểm theo tiếng Việt và múi giờ Asia/Ho_Chi_Minh', () => {
    expect(formatTopicDateTime('2026-10-19T17:30:00Z')).toContain('20/10/2026')
    expect(formatTopicDateTime('2026-10-19T17:30:00Z')).toContain('00:30')
  })
})
