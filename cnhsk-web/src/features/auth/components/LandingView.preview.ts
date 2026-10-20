import { previewLearner, previewMultiAreaUser } from '@/shared/layout/Layout.preview'

import { getRoleAreas } from '../roleAreas'
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
  learner: {
    ...guestBase,
    areas: getRoleAreas(previewLearner.roles),
    displayName: previewLearner.displayName,
  },
  multiArea: {
    ...guestBase,
    areas: getRoleAreas(previewMultiAreaUser.roles),
    displayName: previewMultiAreaUser.displayName,
  },
  noArea: {
    ...guestBase,
    areas: [],
    displayName: 'Người dùng chưa có khu vực',
    state: 'empty',
  },
} satisfies Record<string, LandingViewProps>
