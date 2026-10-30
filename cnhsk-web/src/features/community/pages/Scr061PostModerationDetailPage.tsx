import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { approvePost, getModerationPost, rejectPost } from '../api/moderation'
import { InteractionError } from '../components/InteractionError'
import { ModeratorSession } from '../components/ModeratorSession'
import { useModeratorAction } from '../use-moderator-action'
import { PostModerationComments } from '../components/PostModerationComments'
import { PostModerationDetailView } from '../components/PostModerationDetailView'
import { PostReviewForm } from '../components/PostReviewForm'
import { validateText } from '../interaction-validation'
import { parseModerationPage } from '../moderation-list-query'
import { parsePostId } from '../postForm'
import type { RejectWrite } from '../types'
import { useModeratorDraft } from '../use-moderator-draft'

function PostDetail({ id, userId, queuePath }: { id: number; userId: number; queuePath: string }) {
  const [revision, setRevision] = useState(0)
  const [commentsOpen, setCommentsOpen] = useState(false)
  const [actionError, setActionError] = useState<unknown>()
  const query = useApiQuery(async (signal) => ({ post: await getModerationPost(id, signal), revision }), [id, revision])
  const post = query.state === 'ready' && query.data?.revision === revision ? query.data.post : undefined
  const draft = useModeratorDraft(userId, 'SCR-061', id, 'rejectionReason')
  const action = useModeratorAction()
  const navigate = useNavigate()
  const canReview = post?.status === 'PENDING' && post.author.id !== userId
  const refresh = () => setRevision((current) => current + 1)
  const toQueue = (code: 'postApproved' | 'postRejected' | 'postConflict') => {
    if (code !== 'postConflict') draft.clear()
    navigate(queuePath, { state: { moderationFlash: { code, userId } } })
  }
  const fail = (error: unknown) => {
    if (error instanceof ApiError && error.status === 409 && error.errorCode === 'POST_NOT_PENDING') toQueue('postConflict')
    else setActionError(error)
  }
  const approve = () => {
    if (!canReview) return
    action.run(() => approvePost(id), () => toQueue('postApproved'), fail)
  }
  const reject = (body: RejectWrite) => {
    if (!canReview || validateText(body.reason, 1000)) return
    action.run(() => rejectPost(id, { reason: body.reason.trim() }), () => toQueue('postRejected'), fail)
  }
  return <main className="mx-auto max-w-reading p-4 sm:p-6">
    <Link className="underline" to={queuePath}>Về hàng đợi bài viết</Link>
    <p className="mt-3 text-body-sm text-ink-secondary">Chi tiết kiểm duyệt bài viết</p>
    {query.state === 'error' ? <>{query.error?.status === 404 ? <h1>Không tìm thấy bài viết</h1> : null}<InteractionError error={query.error} /><Button onClick={refresh}>Thử lại bài viết</Button></> : !post ? <p role="status">Đang tải bài viết…</p> : <>
      <PostModerationDetailView post={post} />
      {post.author.id === userId ? <p role="status">Bạn không thể kiểm duyệt bài viết của chính mình.</p> : post.status !== 'PENDING' ? <p role="status">Bài viết không còn chờ duyệt. Chỉ có thể xem thông tin kiểm duyệt.</p> : null}
      <Button disabled={action.busy} variant="outline" onClick={refresh}>Tải lại bài viết</Button>
    </>}
    <div hidden={!canReview}>
      <InteractionError error={actionError} />
      <PostReviewForm reason={draft.text} onReasonChange={draft.setText} disabled={!canReview || action.busy} onApprove={approve} onReject={reject} onCancel={() => { draft.clear(); navigate(queuePath) }} />
    </div>
    {post ? <section aria-label="Bình luận của bài viết" className="my-6">
      <Button variant="outline" aria-expanded={commentsOpen} aria-controls="moderation-post-comments" onClick={() => setCommentsOpen((current) => !current)}>Bình luận</Button>
      {commentsOpen ? <div id="moderation-post-comments"><PostModerationComments postId={post.id} userId={userId} screenId="SCR-061" resourceId={id} onChanged={refresh} /></div> : null}
    </section> : null}
  </main>
}

export default function Scr061PostModerationDetailPage() {
  const { id: rawId } = useParams()
  const [searchParams] = useSearchParams()
  const page = parseModerationPage(searchParams.get('page'))
  const queuePath = page === 0 ? '/moderation/posts' : `/moderation/posts?page=${page}`
  const id = parsePostId(rawId)
  return <ModeratorSession sessionKey={`SCR-061:${rawId ?? ''}`}>{(userId) => id === undefined ? <main className="mx-auto max-w-reading p-6"><h1>Không tìm thấy bài viết</h1><Link to={queuePath}>Về hàng đợi bài viết</Link></main> : <PostDetail id={id} userId={userId} queuePath={queuePath} />}</ModeratorSession>
}
