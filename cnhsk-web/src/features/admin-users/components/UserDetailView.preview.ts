import type { AdminUserDetail } from '../types'
import type { UserDetailViewProps } from './UserDetailView'

export const userDetailPreviewData: AdminUserDetail = {
  id: 101,
  email: 'minh.anh@example.com',
  displayName: 'Minh Anh',
  roles: [
    { code: 'LEARNER', grantedAt: '2026-09-15T03:30:00Z', grantedBy: 103 },
    { code: 'TEACHER', grantedAt: '2026-10-01T08:00:00Z', grantedBy: null },
  ],
  status: 'ACTIVE',
  emailVerifiedAt: '2026-09-14T11:20:00Z',
  createdAt: '2026-09-14T10:00:00Z',
}

const base = {
  banReason: '',
  data: userDetailPreviewData,
  isSelf: false,
  onBanReasonChange: () => undefined,
  onCancelAction: () => undefined,
  onConfirmAction: () => undefined,
  onRequestAction: () => undefined,
  onRetry: () => undefined,
  onRoleChange: () => undefined,
  pending: false,
  refreshing: false,
  selectedRole: '',
  state: 'ready',
} satisfies UserDetailViewProps

export const userDetailPreviews = {
  ready: base,
  loading: { ...base, data: undefined, state: 'loading' },
  empty: { ...base, data: undefined, state: 'empty' },
  error: {
    ...base,
    data: undefined,
    error: { message: 'Không tải được chi tiết tài khoản.', requestId: 'mau-req-0077' },
    state: 'error',
  },
  banConfirm: {
    ...base,
    action: { kind: 'ban' },
    banReason: 'Vi phạm điều khoản sử dụng',
  },
  banned: {
    ...base,
    data: {
      ...userDetailPreviewData,
      bannedAt: '2026-10-20T02:30:00Z',
      banReason: 'Vi phạm điều khoản sử dụng',
      status: 'BANNED',
    },
  },
} satisfies Record<string, UserDetailViewProps>
