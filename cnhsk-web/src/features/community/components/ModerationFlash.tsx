import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useToast } from '@/shared/ui/useToast'

const messages = {
  postConflict: 'Bài đã được người khác xử lý',
  postApproved: 'Đã duyệt bài viết',
  postRejected: 'Đã từ chối bài viết',
  caseDecided: 'Đã xử lý hồ sơ báo cáo',
  caseConflict: 'Hồ sơ đã được người khác xử lý',
}
export function ModerationFlash({ userId }: { userId: number }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const consumed = useRef<string>()
  useEffect(() => {
    const state: unknown = location.state
    if (!state || typeof state !== 'object' || !('moderationFlash' in state)) return
    const flash = state.moderationFlash
    if (!flash || typeof flash !== 'object' || !('userId' in flash) || !('code' in flash)) return
    const code = flash.code
    if (consumed.current === location.key) return
    consumed.current = location.key
    if (flash.userId === userId && typeof code === 'string') {
      const message = Object.entries(messages).find(([key]) => key === code)?.[1]
      if (message) showToast({ message, tone: code.endsWith('Conflict') ? 'error' : 'success' })
    }
    const { moderationFlash: ignored, ...rest } = state
    void ignored
    navigate(`${location.pathname}${location.search}${location.hash}`, { replace: true, state: Object.keys(rest).length ? rest : null })
  }, [location, navigate, showToast, userId])
  return null
}
