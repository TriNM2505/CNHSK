import { describe, expect, it } from 'vitest'

import { getAuthenticatedDestination } from '@/shared/auth'

import { getRoleAreas } from './roleAreas'
import type { CurrentUser, RoleCode } from './types'

const user: CurrentUser = {
  displayName: 'Người dùng mẫu',
  email: 'user@example.com',
  emailVerified: true,
  id: 1,
  roles: [],
}

describe('roleAreas', () => {
  it('giữ đường dẫn khu vực đã dựng đồng bộ với getAuthenticatedDestination', () => {
    const availableRoles: RoleCode[] = ['LEARNER', 'CONTENT_EDITOR', 'ADMIN']

    for (const role of availableRoles) {
      const [area] = getRoleAreas([role])
      const destination = getAuthenticatedDestination({ ...user, roles: [role] })

      expect(area).toBeDefined()
      if (!area) throw new Error(`Thiếu metadata khu vực cho role ${role}.`)
      expect(area.status).toBe('available')
      expect(destination.type).toBe('internal')
      if (destination.type === 'internal') expect(area.to).toBe(destination.to)
    }
  })

  it('đánh dấu đúng các khu vực chưa được dựng', () => {
    expect(getRoleAreas(['TEACHER', 'MODERATOR', 'ACCOUNTANT'])).toEqual([
      expect.objectContaining({ label: 'Giáo viên', status: 'under-construction' }),
      expect.objectContaining({ label: 'Kiểm duyệt', status: 'under-construction' }),
      expect.objectContaining({ label: 'Kế toán', status: 'under-construction' }),
    ])
  })
})
