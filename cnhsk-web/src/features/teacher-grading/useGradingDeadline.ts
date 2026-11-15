import { useEffect, useState } from 'react'

export function useGradingDeadline(deadline?: string): number | null {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    setNow(Date.now())
    if (!deadline) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [deadline])
  return deadline ? Math.max(0, Math.ceil((Date.parse(deadline) - now) / 1000)) : null
}
export function deadlineLabel(seconds: number | null): string {
  if (seconds === null) return '—'
  if (seconds === 0) return 'Hết hạn chấm'
  const hours = Math.floor(seconds / 3600),
    minutes = Math.floor((seconds % 3600) / 60),
    rest = seconds % 60
  return `${hours} giờ ${minutes} phút ${rest} giây`
}
