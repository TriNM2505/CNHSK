import { LandingView, type LandingViewState } from '../components/LandingView'
import { getRoleAreas } from '../roleAreas'
import { useCurrentUser } from '@/shared/auth'

export default function Scr001LandingPage() {
  const { reloadCurrentUser, status, user } = useCurrentUser()
  const areas = user ? getRoleAreas(user.roles) : []
  const state: LandingViewState =
    status === 'loading'
      ? 'loading'
      : status === 'error'
        ? 'error'
        : status === 'authenticated' && areas.length === 0
          ? 'empty'
          : 'ready'

  return (
    <LandingView
      areas={areas}
      displayName={status === 'authenticated' ? user?.displayName : undefined}
      onRetry={() => void reloadCurrentUser()}
      state={state}
    />
  )
}
