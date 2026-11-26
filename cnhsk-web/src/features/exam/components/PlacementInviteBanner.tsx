import { useContext, useState } from 'react'
import { GraduationCap } from 'lucide-react'

import { useApiQuery } from '@/shared/api'
import { AuthContext } from '@/shared/auth'
import { Button } from '@/shared/ui'

import { getPlacementSummary } from '../api/placement'

function dismissKey(userId: number | undefined) {
  return `placement-invite:dismissed:${userId ?? 'guest'}`
}

function readDismissed(key: string): boolean {
  try {
    return window.localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

/**
 * Invites learners who never took the placement test to take it, so they start at the right HSK level.
 * It stays hidden once a test is finished or in progress, while the summary cannot be read, or after "Để sau".
 */
export function PlacementInviteBanner() {
  // The dashboard is always signed in; reading the context directly keeps the banner usable in page tests.
  const user = useContext(AuthContext)?.user
  const key = dismissKey(user?.id)
  const [dismissed, setDismissed] = useState(() => readDismissed(key))
  const summary = useApiQuery((signal) => getPlacementSummary(signal), [])
  if (dismissed || summary.state !== 'ready' || summary.data?.last || summary.data?.active)
    return null
  return (
    <section
      aria-label="Kiểm tra trình độ"
      className="mb-6 flex flex-wrap items-center gap-4 rounded-md border border-brand-100 bg-brand-50 p-4 sm:p-5"
    >
      <span className="flex size-12 shrink-0 items-center justify-center rounded-md bg-brand-100 text-brand-700">
        <GraduationCap aria-hidden="true" className="size-6" />
      </span>
      <div className="min-w-0 flex-1 space-y-1">
        <p className="font-semibold text-ink">Chưa biết mình ở trình độ nào?</p>
        <p className="text-body-sm text-ink-secondary">
          Làm bài kiểm tra 30 câu trong khoảng 15 phút để biết bạn đang ở HSK 1, 2 hay 3 và học đúng
          cấp.
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button to="/learn/placement">Làm bài kiểm tra</Button>
        <Button
          variant="ghost"
          onClick={() => {
            setDismissed(true)
            try {
              window.localStorage.setItem(key, '1')
            } catch {
              /* Without storage the banner only stays hidden on this page. */
            }
          }}
        >
          Để sau
        </Button>
      </div>
    </section>
  )
}
