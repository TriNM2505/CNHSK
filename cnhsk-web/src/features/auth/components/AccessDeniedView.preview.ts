import type { AccessDeniedViewProps } from './AccessDeniedView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const guestBase = {
  mode: 'guest',
  onRetry: () => undefined,
  state: 'ready',
} satisfies AccessDeniedViewProps

export const accessDeniedPreviews = {
  guest: guestBase,
  loading: { ...guestBase, state: 'loading' },
  sessionError: { ...guestBase, state: 'error' },
  forbidden: {
    ...guestBase,
    mode: 'forbidden',
    returnLabel: 'Về khu vực được phép',
    returnTo: '/learn/topics',
  },
  underConstruction: {
    ...guestBase,
    areaLabels: ['Giáo viên'],
    mode: 'under-construction',
  },
  noArea: {
    ...guestBase,
    mode: 'forbidden',
    returnLabel: 'Về trang chủ',
    returnTo: '/',
    state: 'empty',
  },
} satisfies Record<string, AccessDeniedViewProps>
