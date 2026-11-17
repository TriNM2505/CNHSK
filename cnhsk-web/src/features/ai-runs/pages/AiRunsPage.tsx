import { useEffect, useRef, useState } from 'react'
import { toApiError, useApiQuery } from '@/shared/api'
import { listAiRuns, triggerAiRun } from '../api/ai-runs'
import { AiRunsView } from '../components/AiRunsView'
import type { RunStatus } from '../types'

export default function AiRunsPage() {
  const [filter, setFilter] = useState<{ status: RunStatus | ''; page: number }>({
    status: '',
    page: 0,
  })
  const query = useApiQuery(
    (signal) => listAiRuns({ status: filter.status || undefined, page: filter.page }, signal),
    [filter.status, filter.page],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const pending = useRef(false)
  const mounted = useRef(false)
  const request = useRef<AbortController>()
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string>()
  const [triggerError, setTriggerError] = useState<{ message: string; requestId?: string }>()
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      request.current?.abort()
    }
  }, [])

  async function trigger() {
    if (pending.current || !query.data?.enabled || !['ready', 'empty'].includes(query.state)) return
    pending.current = true
    setBusy(true)
    setNotice(undefined)
    setTriggerError(undefined)
    const controller = new AbortController()
    request.current = controller
    try {
      await triggerAiRun(controller.signal)
      if (!mounted.current) return
      setNotice(
        'Đã tiếp nhận yêu cầu chạy lô. Lô chạy nền và chưa hoàn tất; làm mới để xem tiến độ.',
      )
      query.reload()
    } catch (failure) {
      if (!mounted.current) return
      const error = toApiError(failure)
      const message =
        error.errorCode === 'AI_GENERATION_BUSY'
          ? 'Một lô khác đang chạy. Hãy làm mới lịch sử trước khi thử lại.'
          : error.errorCode === 'AI_GENERATION_DISABLED'
            ? 'Chức năng chạy lô đã tắt. Đang cập nhật trạng thái.'
            : error.status === 401
              ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
              : error.status === 403
                ? 'Bạn không có quyền chạy lô.'
                : 'Chưa xác nhận được yêu cầu. Hãy làm mới lịch sử trước khi chủ động thử lại để tránh chạy trùng.'
      setTriggerError({ message, requestId: error.requestId })
      if (error.errorCode === 'AI_GENERATION_DISABLED' || error.errorCode === 'AI_GENERATION_BUSY')
        query.reload()
    } finally {
      pending.current = false
      if (mounted.current) setBusy(false)
    }
  }
  return (
    <AiRunsView
      {...query}
      {...filter}
      busy={busy}
      notice={notice}
      triggerError={triggerError}
      onTrigger={() => void trigger()}
      onReload={query.reload}
      onStatus={(status) => setFilter({ status, page: 0 })}
      onPage={(page) => setFilter((current) => ({ ...current, page }))}
    />
  )
}
