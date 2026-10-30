import { useState, type ReactNode } from 'react'
import { useCurrentUser } from '@/shared/auth'
import { ToastProvider } from '@/shared/ui/Toast'
import { useSessionAction } from '../use-session-action'
import { cleanModeratorDrafts } from '../moderator-drafts'
import { ModeratorActionContext, type ModeratorActionLane } from '../use-moderator-action'

function SessionLane({ children, userId }: { children: ReactNode; userId: number }) {
  useState(() => { cleanModeratorDrafts(userId); return null })
  const action = useSessionAction<() => void>()
  const run: ModeratorActionLane['run'] = (operation, onSuccess, onError) => {
    action.run(async () => {
      const result = await operation()
      return () => onSuccess(result)
    }, (deliver) => deliver(), onError)
  }
  return <ModeratorActionContext.Provider value={{ busy: action.busy, run }}>{children}</ModeratorActionContext.Provider>
}

export function ModeratorSession({ sessionKey, children }: { sessionKey: string; children: (userId: number) => ReactNode }) {
  const auth = useCurrentUser()
  if (auth.status === 'loading') return <p role="status">Đang kiểm tra quyền kiểm duyệt…</p>
  if (auth.status === 'error') return <section><p role="alert">Không thể xác minh quyền kiểm duyệt.</p><button type="button" onClick={() => void auth.reloadCurrentUser()}>Thử lại quyền kiểm duyệt</button></section>
  const user = auth.status === 'authenticated' ? auth.user : null
  if (!user?.roles.includes('MODERATOR') || !Number.isSafeInteger(user.id) || user.id <= 0) return <p role="alert">Không có quyền kiểm duyệt.</p>
  return <ToastProvider key={`${sessionKey}:${user.id}:${user.roles.slice().sort().join(',')}`}><SessionLane userId={user.id}>{children(user.id)}</SessionLane></ToastProvider>
}
