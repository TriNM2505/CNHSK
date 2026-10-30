import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { decideReportCase, getReportCase } from '../api/moderation'
import { InteractionError } from '../components/InteractionError'
import { ModeratorSession } from '../components/ModeratorSession'
import { useModeratorAction } from '../use-moderator-action'
import { ReportCaseDetailView } from '../components/ReportCaseDetailView'
import { ReportDecisionForm } from '../components/ReportDecisionForm'
import { ReportTargetModeration } from '../components/ReportTargetModeration'
import { makeReportDecision } from '../moderation-actions'
import { parseModerationPage } from '../moderation-list-query'
import { parsePostId } from '../postForm'
import type { ReportDecision } from '../types'
import { useModeratorDraft } from '../use-moderator-draft'

function ReportCaseController({ id, userId, queuePath }: { id: number; userId: number; queuePath: string }) {
  const navigate = useNavigate()
  const draft = useModeratorDraft(userId, 'SCR-063', id, 'note')
  const [decision, setDecision] = useState<ReportDecision['decision'] | ''>('')
  const [commentAction, setCommentAction] = useState<NonNullable<ReportDecision['commentAction']> | ''>('')
  const [confirming, setConfirming] = useState(false)
  const [panelOpen, setPanelOpen] = useState(false)
  const [error, setError] = useState<unknown>()
  const [locked, setLocked] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [revision, setRevision] = useState(0)
  const action = useModeratorAction()
  const query = useApiQuery(async (signal) => ({ reportCase: await getReportCase(id, signal), revision }), [id, revision])
  const reportCase = query.state === 'ready' && query.data?.revision === revision ? query.data.reportCase : undefined
  const refresh = () => setRevision((value) => value + 1)

  useEffect(() => {
    if (conflict && reportCase && reportCase.status !== 'PENDING') {
      navigate(queuePath, { state: { moderationFlash: { code: 'caseConflict', userId } } })
    }
  }, [conflict, reportCase, navigate, userId, queuePath])

  const allowed = Boolean(reportCase && reportCase.status === 'PENDING' && reportCase.target.author?.id !== userId && !locked)
  const submit = (body: ReportDecision) => {
    if (!allowed || !reportCase || !confirming || action.busy) return
    const currentBody = makeReportDecision(reportCase.target.kind, decision, draft.text, commentAction)
    if (!currentBody || JSON.stringify(currentBody) !== JSON.stringify(body)) return
    setError(undefined)
    action.run(() => decideReportCase(id, currentBody), () => {
      draft.clear()
      navigate(queuePath, { state: { moderationFlash: { code: 'caseDecided', userId } } })
    }, (caught) => {
      setError(caught)
      if (caught instanceof ApiError && caught.status === 409 && caught.errorCode === 'REPORT_ALREADY_HANDLED') { setLocked(true); setConflict(true); refresh() }
      else if (caught instanceof ApiError && caught.errorCode === 'SELF_MODERATION') setLocked(true)
      else if (caught instanceof ApiError && caught.status === 404) refresh()
    })
  }
  return <main className="mx-auto max-w-reading space-y-4 p-4 sm:p-6">
    <Link className="underline" to={queuePath}>Về hàng đợi báo cáo</Link>
    <InteractionError error={error} />
    {query.state === 'error' ? <>{query.error?.status === 404 ? <h1>Không tìm thấy hồ sơ báo cáo</h1> : null}<InteractionError error={query.error} /><Button onClick={refresh}>Thử lại hồ sơ</Button></> : !reportCase ? <p role="status">Đang tải hồ sơ báo cáo…</p> : <>
      <ReportCaseDetailView reportCase={reportCase} />
      {reportCase.status === 'PENDING' && reportCase.target.author?.id === userId ? <p role="status">Không thể xử lý báo cáo về nội dung của chính mình.</p> : null}
      {locked ? <p role="status">{conflict ? 'Hồ sơ đã bị khoá quyết định. Đang xác minh kết quả xử lý từ máy chủ.' : 'Máy chủ không cho phép bạn xử lý hồ sơ này.'}</p> : null}
      {allowed ? <ReportDecisionForm targetKind={reportCase.target.kind} note={draft.text} onNoteChange={draft.setText} decision={decision} onDecisionChange={setDecision} commentAction={commentAction} onCommentActionChange={setCommentAction} confirming={confirming} onConfirmingChange={setConfirming} disabled={action.busy} error={error} onSubmit={submit} onCancel={() => { draft.clear(); navigate(queuePath) }} /> : null}
      {reportCase.target.kind === 'COMMENT' ? <section aria-label="Kiểm duyệt bình luận độc lập">
        <Button variant="outline" disabled={action.busy} onClick={() => setPanelOpen((value) => !value)}>{panelOpen ? 'Đóng kiểm duyệt bình luận' : 'Kiểm duyệt bình luận'}</Button>
        {panelOpen ? <ReportTargetModeration target={reportCase.target} userId={userId} caseId={id} onChanged={refresh} /> : null}
      </section> : null}
    </>}
  </main>
}

export default function Scr063ViolationReportDetailPage() {
  const { id: rawId } = useParams()
  const [searchParams] = useSearchParams()
  const page = parseModerationPage(searchParams.get('page'))
  const queuePath = page === 0 ? '/moderation/reports' : `/moderation/reports?page=${page}`
  const id = parsePostId(rawId)
  return <ModeratorSession sessionKey={`SCR-063:${rawId ?? ''}`}>{(userId) => id === undefined ? <main className="p-6"><h1>Không tìm thấy hồ sơ báo cáo</h1><Link to={queuePath}>Về hàng đợi báo cáo</Link></main> : <ReportCaseController id={id} userId={userId} queuePath={queuePath} />}</ModeratorSession>
}
