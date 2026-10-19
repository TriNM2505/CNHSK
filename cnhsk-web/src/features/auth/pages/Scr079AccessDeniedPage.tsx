import { AccessDeniedView, type AccessDeniedViewProps } from '../components/AccessDeniedView'
import { getAvailableRoleAreas, getRoleAreas } from '../roleAreas'
import { useCurrentUser } from '@/shared/auth'

type SafeReturn = Pick<AccessDeniedViewProps, 'returnLabel' | 'returnTo'>

function getSafeReturn(areas: ReturnType<typeof getAvailableRoleAreas>): SafeReturn {
  const [area] = areas
  if (areas.length === 1 && area?.to) {
    return {
      returnLabel: `Về khu vực ${area.label.toLocaleLowerCase('vi')}`,
      returnTo: area.to,
    }
  }

  return { returnLabel: 'Về trang chủ', returnTo: '/' }
}

export default function Scr079AccessDeniedPage() {
  const { reloadCurrentUser, status, user } = useCurrentUser()

  if (status === 'loading') {
    return <AccessDeniedView mode="guest" onRetry={() => undefined} state="loading" />
  }

  if (status !== 'authenticated' || !user) {
    return (
      <AccessDeniedView
        mode="guest"
        onRetry={() => void reloadCurrentUser()}
        state={status === 'error' ? 'error' : 'ready'}
      />
    )
  }

  const areas = getRoleAreas(user.roles)
  const availableAreas = getAvailableRoleAreas(user.roles)
  if (availableAreas.length === 0 && areas.some((area) => area.status === 'under-construction')) {
    return (
      <AccessDeniedView
        areaLabels={areas.map((area) => area.label)}
        mode="under-construction"
        onRetry={() => undefined}
        state="ready"
      />
    )
  }

  const safeReturn = getSafeReturn(availableAreas)
  return (
    <AccessDeniedView
      mode="forbidden"
      onRetry={() => undefined}
      state={areas.length === 0 ? 'empty' : 'ready'}
      {...safeReturn}
    />
  )
}
