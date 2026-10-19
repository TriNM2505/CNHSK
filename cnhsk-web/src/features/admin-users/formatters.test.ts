import { describe, expect, it } from 'vitest'

import { formatAdminUserDate } from './formatters'

describe('admin user formatters', () => {
  it('định dạng ngày theo múi giờ Asia/Ho_Chi_Minh, không phụ thuộc múi giờ máy chạy test', () => {
    expect(formatAdminUserDate('2026-10-19T17:30:00Z')).toBe('20/10/26')
  })
})
