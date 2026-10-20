import { describe, expect, it } from 'vitest'

import { parseAdminUserSearchParams, toAdminUserSearchParams } from './userListQuery'

describe('admin user list URL query', () => {
  it.each([
    ['role=FOO', 'role'],
    ['status=locked', 'status'],
    ['page=-1', 'page'],
    ['page=abc', 'page'],
    ['q=a', 'q'],
  ])('loại bỏ tham số không hợp lệ: %s', (search, key) => {
    const result = parseAdminUserSearchParams(new URLSearchParams(search))

    expect(result.query).toEqual({ page: 0, q: '', role: '', status: '' })
    expect(result.canonical.has(key)).toBe(false)
    expect(result.needsCleanup).toBe(true)
  })

  it('đọc bộ lọc hợp lệ, cắt khoảng trắng và dùng trang bắt đầu từ 0', () => {
    const result = parseAdminUserSearchParams(
      new URLSearchParams('q=%20minh%20&role=LEARNER&status=ACTIVE&page=2'),
    )

    expect(result.query).toEqual({ page: 2, q: 'minh', role: 'LEARNER', status: 'ACTIVE' })
    expect(result.canonical.get('q')).toBe('minh')
  })

  it('ghi query chuẩn và giữ tham số không thuộc màn hình', () => {
    const params = toAdminUserSearchParams(
      { page: 1, q: 'minh', role: 'ADMIN', status: 'BANNED' },
      new URLSearchParams('source=menu&page=99'),
    )

    expect(params.toString()).toBe('source=menu&q=minh&role=ADMIN&status=BANNED&page=1')
  })
})
