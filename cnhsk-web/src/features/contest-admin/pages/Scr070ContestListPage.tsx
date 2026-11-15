import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ConfirmPanel } from '@/shared/ui'
import { listContests, transitionContest } from '../api/contests'
import { ContestListView } from '../components/ContestListView'
import { actionLabels, errorMessage, isConflict } from '../contestActions'
import type { AdminContest, ContestAction, ContestStatus } from '../types'

function Management() {
  const [search, setSearch] = useSearchParams()
  const rawStatus = search.get('status')
  const status = (['DRAFT', 'PUBLISHED', 'CANCELLED'] as const).find((item) => item === rawStatus)
  const rawPage = search.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const query = useApiQuery(
    (signal) => listContests({ status, page, size: 20 }, signal),
    [status, page],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const [confirmation, setConfirmation] = useState<{
    contest: AdminContest
    action: ContestAction
  }>()
  const [notice, setNotice] = useState<string>()
  const mutation = useApiMutation(({ id, action }: { id: number; action: ContestAction }) =>
    transitionContest(id, action),
  )
  useEffect(() => {
    const next = query.data?.items
      .flatMap((item) => [Date.parse(item.startsAt), Date.parse(item.endsAt)])
      .filter((time) => time > Date.now())
      .sort((a, b) => a - b)[0]
    if (next === undefined) return
    const timer = window.setTimeout(query.reload, Math.min(next - Date.now() + 100, 2_147_483_647))
    return () => window.clearTimeout(timer)
    // Reload is intentionally triggered by a new server response, not by every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query.data])
  function filter(nextStatus: ContestStatus | '', nextPage = 0) {
    const params = new URLSearchParams()
    if (nextStatus) params.set('status', nextStatus)
    if (nextPage) params.set('page', String(nextPage))
    setSearch(params)
  }
  return (
    <>
      {notice && (
        <p className="mb-4" role="status">
          {notice}
        </p>
      )}
      {mutation.error && !confirmation && (
        <p className="mb-4" role="alert">
          {errorMessage(mutation.error)}
        </p>
      )}
      <ContestListView
        state={query.state}
        data={query.data}
        error={query.error?.message}
        status={status ?? ''}
        busy={mutation.pending}
        onFilter={(value) => filter(value)}
        onPage={(value) => filter(status ?? '', value)}
        onRetry={query.reload}
        onAction={(contest, action) => {
          mutation.reset()
          setNotice(undefined)
          setConfirmation({ contest, action })
        }}
      />
      <ConfirmPanel
        open={!!confirmation}
        pending={mutation.pending}
        danger={confirmation?.action === 'cancel'}
        title={confirmation ? actionLabels[confirmation.action] : ''}
        message={
          <>
            <p>
              {confirmation?.contest.name}
              {confirmation?.action === 'ranking'
                ? ': kết quả sẽ được công khai và không thể xét lại thí sinh.'
                : ': cuộc thi sẽ bị huỷ và giữ lịch sử.'}
            </p>
            {mutation.error && (
              <p className="mt-3" role="alert">
                {errorMessage(mutation.error)}
              </p>
            )}
          </>
        }
        confirmLabel="Xác nhận"
        cancelLabel="Bỏ qua"
        onCancel={() => setConfirmation(undefined)}
        onConfirm={() => {
          if (!confirmation) return
          void mutation
            .run({ id: confirmation.contest.id, action: confirmation.action })
            .then(() => {
              setNotice('Đã cập nhật cuộc thi.')
              setConfirmation(undefined)
              query.reload()
            })
            .catch((failure: unknown) => {
              if (isConflict(failure)) {
                setConfirmation(undefined)
                query.reload()
              }
            })
        }}
      />
    </>
  )
}
export default function Scr070ContestListPage() {
  const auth = useCurrentUser()
  if (!auth.user?.roles.includes('CONTENT_EDITOR'))
    return <p role="alert">Cần quyền biên tập nội dung để quản lý cuộc thi.</p>
  return <Management key={auth.user.id} />
}
