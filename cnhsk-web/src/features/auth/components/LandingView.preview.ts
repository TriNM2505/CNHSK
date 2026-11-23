import { previewLearner, previewMultiAreaUser } from '@/shared/layout/Layout.preview'

import type { LandingViewProps } from './LandingView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const guestBase = {
  onRetry: () => undefined,
  state: 'ready',
} satisfies LandingViewProps

export const landingPreviews = {
  guest: guestBase,
  loading: { ...guestBase, state: 'loading' },
  error: { ...guestBase, state: 'error' },
  learner: { ...guestBase, displayName: previewLearner.displayName },
  multiArea: { ...guestBase, displayName: previewMultiAreaUser.displayName },
  noArea: { ...guestBase, displayName: 'Người dùng chưa có vai trò' },
} satisfies Record<string, LandingViewProps>
