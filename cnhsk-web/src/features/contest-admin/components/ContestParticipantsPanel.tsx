import { useState } from 'react'
import type { ApiQueryState } from '@/shared/api'
import { useApiMutation } from '@/shared/api'
import { Button, Card, DataTable, FormField, Skeleton, StatusBadge, Textarea } from '@/shared/ui'
import { duration, errorMessage, isConflict, vietnamTime } from '../contestActions'
import type { Participant, ParticipantDecision } from '../types'

const attemptStatusLabels: Record<string, string> = {
  IN_PROGRESS: 'Đang làm bài',
  SUBMITTED: 'Đã nộp',
  EXPIRED: 'Hết hạn',
  ABANDONED: 'Đã bỏ dở',
}

type Props = {
  state: ApiQueryState
  participants?: Participant[]
  error?: string
  published: boolean
  disabled?: boolean
  onReload: () => void
  onConflict: (failure?: unknown) => void
  onDecision: (userId: number, input: ParticipantDecision) => Promise<unknown>
}
export function ContestParticipantsPanel({
  state,
  participants,
  error,
  published,
  disabled,
  onReload,
  onConflict,
  onDecision,
}: Props) {
  const [decision, setDecision] = useState<{ userId: number; name: string; excluded: boolean }>()
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const mutation = useApiMutation(
    ({ userId, input }: { userId: number; input: ParticipantDecision }) =>
      onDecision(userId, input),
  )
  function confirm() {
    if (!decision || mutation.pending || published || disabled) return
    if (!note.trim() || note.length > 500) {
      setNoteError('Ghi chú bắt buộc có 1–500 ký tự.')
      document.getElementById('participant-note')?.focus()
      return
    }
    void mutation
      .run({ userId: decision.userId, input: { excluded: decision.excluded, note: note.trim() } })
      .then(() => {
        setNotice('Đã ghi nhận quyết định của ban tổ chức.')
        setDecision(undefined)
        setNote('')
        onReload()
      })
      .catch((failure: unknown) => {
        if (isConflict(failure)) {
          setDecision(undefined)
          setNote('')
          onConflict(failure)
        }
      })
  }
  return (
    <Card className="min-w-0 space-y-6 p-6">
      <h2 className="text-h2">Thí sinh</h2>
      <p className="text-body-sm text-ink-secondary">
        Chuyển tab chỉ là thông tin để xét. Cờ đáng ngờ loại lượt khỏi bảng xếp hạng cho tới khi ban
        tổ chức chọn Giữ.
      </p>
      {published && <p role="status">Kết quả đã công bố, không thể thay đổi quyết định.</p>}
      {notice && <p role="status">{notice}</p>}
      {mutation.error && <p role="alert">{errorMessage(mutation.error)}</p>}
      {state === 'loading' && (
        <div>
          <p role="status">Đang tải thí sinh…</p>
          <Skeleton variant="table" />
        </div>
      )}
      {state === 'error' && (
        <div role="alert">
          <p>{error}</p>
          <Button onClick={onReload} variant="outline">
            Thử tải thí sinh lại
          </Button>
        </div>
      )}
      {state === 'empty' && <p>Chưa có lượt thi.</p>}
      {state === 'ready' && participants && (
        <DataTable
          caption="Thí sinh cuộc thi"
          rows={participants}
          rowKey={(row) => row.userId}
          columns={[
            {
              id: 'name',
              header: 'Thí sinh',
              render: (row) => (
                <div className="break-words">
                  <p>{row.displayName ?? `Người dùng #${row.userId}`}</p>
                  <StatusBadge tone={row.status === 'SUBMITTED' ? 'success' : 'neutral'}>
                    {attemptStatusLabels[row.status] ?? 'Trạng thái lượt chưa được hỗ trợ'}
                  </StatusBadge>
                </div>
              ),
            },
            {
              id: 'result',
              header: 'Bài thi',
              render: (row) => (
                <div>
                  <p>
                    {row.score === null || row.maxScore === null
                      ? 'Chưa có điểm'
                      : `${row.score}/${row.maxScore} câu đúng`}
                  </p>
                  <p>{duration(row.durationMs)}</p>
                  <p>Nộp: {vietnamTime(row.submittedAt)}</p>
                </div>
              ),
            },
            {
              id: 'flags',
              header: 'Ghi nhận',
              render: (row) => (
                <div>
                  <StatusBadge
                    className="max-w-full whitespace-normal"
                    tone={row.suspicious ? 'warning' : 'neutral'}
                  >
                    {row.suspicious ? 'Đáng ngờ / đang bị loại' : 'Không bị loại'}
                  </StatusBadge>
                  <p>Chuyển tab: {row.cheatEventCount} lần</p>
                </div>
              ),
            },
            {
              id: 'decision',
              header: 'Xét lượt',
              render: (row) => (
                <div className="space-y-2">
                  {[true, false].map((excluded) => (
                    <Button
                      key={String(excluded)}
                      size="sm"
                      variant={excluded ? 'danger' : 'outline'}
                      disabled={
                        published || disabled || mutation.pending || row.status !== 'SUBMITTED'
                      }
                      onClick={() => {
                        setDecision({
                          userId: row.userId,
                          name: row.displayName ?? `Người dùng #${row.userId}`,
                          excluded,
                        })
                        setNote('')
                        setNoteError(undefined)
                        mutation.reset()
                        setNotice(undefined)
                      }}
                    >
                      {excluded ? 'Loại' : 'Giữ'}
                    </Button>
                  ))}
                  {row.status !== 'SUBMITTED' && (
                    <p className="text-caption">Chỉ xét lượt đã nộp.</p>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}
      {decision && !published && (
        <form
          aria-label="Xác nhận xét thí sinh"
          className="space-y-4 rounded-md border border-warning bg-warning-bg p-6"
          onSubmit={(e) => {
            e.preventDefault()
            confirm()
          }}
        >
          <p className="font-semibold">
            {decision.excluded ? 'Loại' : 'Giữ'}: {decision.name}
          </p>
          <FormField
            htmlFor="participant-note"
            label="Ghi chú quyết định"
            required
            error={noteError}
          >
            <Textarea
              disabled={mutation.pending || disabled}
              maxLength={500}
              value={note}
              onChange={(e) => {
                setNote(e.target.value)
                setNoteError(undefined)
              }}
            />
          </FormField>
          <div className="flex flex-wrap gap-2">
            <Button disabled={disabled} loading={mutation.pending} type="submit">
              Xác nhận {decision.excluded ? 'loại' : 'giữ'}
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={mutation.pending}
              onClick={() => {
                setDecision(undefined)
                setNote('')
              }}
            >
              Bỏ qua
            </Button>
          </div>
        </form>
      )}
    </Card>
  )
}
