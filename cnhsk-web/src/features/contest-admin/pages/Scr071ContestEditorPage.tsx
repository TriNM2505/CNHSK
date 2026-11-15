import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { Button, Card, ConfirmPanel, PageHeader } from '@/shared/ui'
import {
  decideParticipant,
  findAttachedExam,
  getContest,
  listExams,
  listParticipants,
  revealWinners,
  saveContest,
  transitionContest,
} from '../api/contests'
import { ContestEditorForm } from '../components/ContestEditorForm'
import { ContestParticipantsPanel } from '../components/ContestParticipantsPanel'
import { ContestWinnersPanel } from '../components/ContestWinnersPanel'
import {
  actionLabels,
  actionReason,
  editReason,
  errorMessage,
  isConflict,
  stateLabels,
  statusLabels,
  vietnamTime,
} from '../contestActions'
import type { ContestAction, ContestWrite, ExamListParams } from '../types'

function Participants({
  id,
  published,
  onConflict,
}: {
  id: number
  published: boolean
  onConflict: (failure?: unknown) => void
}) {
  const query = useApiQuery((signal) => listParticipants(id, signal), [id], {
    isEmpty: (rows) => rows.length === 0,
  })
  return (
    <ContestParticipantsPanel
      state={query.state}
      participants={query.data}
      error={query.error?.message}
      published={published}
      onReload={query.reload}
      onConflict={onConflict}
      onDecision={(userId, input) => decideParticipant(id, userId, input)}
    />
  )
}

function Editor({ id }: { id: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const listSearch =
    typeof location.state?.contestListSearch === 'string' &&
    /^\?/.test(location.state.contestListSearch)
      ? location.state.contestListSearch
      : ''
  const listPath = `/content/contests${listSearch}`
  const detail = useApiQuery(
    (signal) => (id === null ? Promise.resolve(null) : getContest(id, signal)),
    [id],
  )
  const [examFilters, setExamFilters] = useState<ExamListParams>({ page: 0 })
  const exams = useApiQuery((signal) => listExams(examFilters, signal), [examFilters])
  const [selectedId, setSelectedId] = useState<number | null>()
  const currentExamId = selectedId === undefined ? (detail.data?.examId ?? null) : selectedId
  const [revision, setRevision] = useState(0)
  const attached = useApiQuery(
    (signal) =>
      currentExamId === null ? Promise.resolve(null) : findAttachedExam(currentExamId, signal),
    [currentExamId, revision],
  )
  const [dirty, setDirty] = useState(false)
  const [updateLocked, setUpdateLocked] = useState(false)
  const [notice, setNotice] = useState<string>()
  const [confirmation, setConfirmation] = useState<ContestAction>()
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  type Operation = { type: 'save'; input: ContestWrite } | { type: ContestAction }
  const mutation = useApiMutation((operation: Operation) =>
    operation.type === 'save'
      ? saveContest(id, operation.input)
      : transitionContest(id!, operation.type),
  )
  function reload() {
    setSelectedId(undefined)
    setDirty(false)
    setRevision((value) => value + 1)
    detail.reload()
  }
  function conflict(failure?: unknown) {
    setNotice(
      failure ? errorMessage(failure) : 'Dữ liệu đã thay đổi. Đang tải lại trạng thái cuộc thi.',
    )
    reload()
  }
  function perform(operation: Operation) {
    void mutation
      .run(operation)
      .then((saved) => {
        if (!alive.current) return
        setConfirmation(undefined)
        setUpdateLocked(false)
        if (id === null) {
          navigate(`/content/contests/${saved.id}`, {
            replace: true,
            state: { contestListSearch: listSearch },
          })
          return
        }
        setNotice(
          operation.type === 'save' ? 'Đã lưu cuộc thi.' : 'Đã cập nhật trạng thái cuộc thi.',
        )
        reload()
      })
      .catch((failure: unknown) => {
        if (!alive.current) return
        if (isConflict(failure)) {
          if (operation.type === 'save' && failure.errorCode === 'CONTEST_LOCKED')
            setUpdateLocked(true)
          setConfirmation(undefined)
          reload()
        } else if (failure instanceof ApiError && failure.errorCode === 'CONTEST_EXAM_INVALID') {
          setConfirmation(undefined)
          exams.reload()
          reload()
        }
      })
  }
  useEffect(() => {
    if (!detail.data || detail.data.status !== 'PUBLISHED') return
    const next = [Date.parse(detail.data.startsAt), Date.parse(detail.data.endsAt)]
      .filter((time) => time > Date.now())
      .sort((a, b) => a - b)[0]
    if (next === undefined) return
    const timer = window.setTimeout(
      () => {
        setConfirmation(undefined)
        reload()
      },
      Math.min(next - Date.now() + 100, 2_147_483_647),
    )
    return () => window.clearTimeout(timer)
    // A server response schedules the next boundary refresh.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail.data])
  if (detail.state === 'loading')
    return (
      <main>
        <p role="status">Đang tải cuộc thi…</p>
      </main>
    )
  if (detail.state === 'error')
    return (
      <main>
        <p role="alert">{detail.error?.message}</p>
        <Button onClick={detail.reload}>Thử tải cuộc thi lại</Button>
        <Link to={listPath}>Về danh sách</Link>
      </main>
    )
  const contest = detail.data ?? null
  const lockedReason = updateLocked
    ? 'Cuộc thi đã bị khoá sửa theo phản hồi máy chủ.'
    : contest
      ? editReason(contest)
      : undefined
  const publishExamReason =
    currentExamId === null
      ? 'Chọn đề thi trước khi công bố.'
      : attached.state === 'loading'
        ? 'Đang kiểm tra đề đã chọn.'
        : attached.state === 'error'
          ? 'Chưa kiểm tra được đề đã chọn. Thử tải đề lại.'
          : attached.data?.status === 'ARCHIVED'
            ? 'Đề đã lưu trữ — chọn đề khác trước khi công bố'
            : !attached.data || attached.data.questionCount === 0
              ? 'Đề chưa có câu hỏi hợp lệ để công bố.'
              : undefined
  return (
    <main className="min-w-0 space-y-6">
      <Link className="inline-flex min-h-touch items-center text-brand-700 underline" to={listPath}>
        Về danh sách cuộc thi
      </Link>
      <PageHeader
        title={contest ? 'Chi tiết cuộc thi' : 'Tạo cuộc thi'}
        subtitle={contest?.name ?? 'Lưu nháp trước khi công bố cuộc thi.'}
      />
      {notice && <p role="status">{notice}</p>}
      {mutation.error && !confirmation && <p role="alert">{errorMessage(mutation.error)}</p>}
      {contest && (
        <Card>
          <p>
            {statusLabels[contest.status]} · {stateLabels[contest.state]}
          </p>
          <p>Kết quả: {vietnamTime(contest.rankingPublishedAt)}</p>
          <p>{contest.participantCount} lượt thi</p>
        </Card>
      )}
      <ContestEditorForm
        key={revision}
        contest={contest}
        lockedReason={lockedReason}
        pending={mutation.pending}
        error={mutation.error}
        examState={exams.state}
        exams={exams.data}
        examError={exams.error?.message}
        examFilters={examFilters}
        onExamFilter={setExamFilters}
        onExamRetry={() => {
          exams.reload()
          attached.reload()
        }}
        selectedExam={attached.data}
        checkingExam={attached.state === 'loading'}
        onSelectedExam={setSelectedId}
        onDirty={() => setDirty(true)}
        onSubmit={(input) => perform({ type: 'save', input })}
      />
      {attached.state === 'error' && <p role="alert">{attached.error?.message}</p>}
      {contest && (
        <Card className="space-y-4">
          <h2 className="text-h3">Công bố và huỷ cuộc thi</h2>
          {(['publish', 'cancel', 'ranking'] as const).map((action) => {
            const reason =
              actionReason(contest, action) ??
              (action === 'publish'
                ? dirty
                  ? 'Lưu thay đổi trước khi công bố.'
                  : publishExamReason
                : undefined)
            return (
              <div key={action} className="space-y-1">
                <Button
                  aria-describedby={reason ? `${action}-reason` : undefined}
                  disabled={mutation.pending || !!reason}
                  variant={action === 'cancel' ? 'danger' : 'outline'}
                  onClick={() => {
                    mutation.reset()
                    setNotice(undefined)
                    setConfirmation(action)
                  }}
                >
                  {actionLabels[action]}
                </Button>
                {reason && (
                  <p className="text-body-sm text-ink-secondary" id={`${action}-reason`}>
                    {reason}
                  </p>
                )}
              </div>
            )
          })}
        </Card>
      )}
      {contest?.state === 'ENDED' && (
        <Participants
          key={`participants-${revision}`}
          id={contest.id}
          published={!!contest.rankingPublishedAt}
          onConflict={conflict}
        />
      )}
      {contest?.status === 'PUBLISHED' && contest.rankingPublishedAt && (
        <ContestWinnersPanel
          key={`contacts-${revision}`}
          onReveal={(signal) => revealWinners(contest.id, signal)}
          onConflict={conflict}
        />
      )}
      <ConfirmPanel
        open={!!confirmation}
        pending={mutation.pending}
        title={confirmation ? actionLabels[confirmation] : ''}
        message={
          <>
            {confirmation === 'ranking'
              ? 'Kết quả sẽ được công khai. Sau đó không thể Loại/Giữ thí sinh.'
              : confirmation === 'cancel'
                ? 'Huỷ cuộc thi này? Lịch sử được giữ nguyên.'
                : 'Công khai cuộc thi với lịch và đề đã lưu?'}
            {mutation.error && (
              <p className="mt-3" role="alert">
                {errorMessage(mutation.error)}
              </p>
            )}
          </>
        }
        danger={confirmation === 'cancel'}
        confirmLabel="Xác nhận"
        cancelLabel="Bỏ qua"
        onCancel={() => setConfirmation(undefined)}
        onConfirm={() => {
          if (confirmation) perform({ type: confirmation })
        }}
      />
    </main>
  )
}
export default function Scr071ContestEditorPage() {
  const auth = useCurrentUser()
  const { id: rawId } = useParams()
  if (!auth.user?.roles.includes('CONTENT_EDITOR'))
    return <p role="alert">Cần quyền biên tập nội dung để quản lý cuộc thi.</p>
  const id = rawId === undefined ? null : Number(rawId)
  if (id !== null && (!/^\d+$/.test(rawId!) || !Number.isSafeInteger(id) || id < 1))
    return (
      <main>
        <p role="alert">Mã cuộc thi không hợp lệ.</p>
        <Link to="/content/contests">Về danh sách</Link>
      </main>
    )
  return <Editor key={`${auth.user.id}-${id ?? 'new'}`} id={id} />
}
