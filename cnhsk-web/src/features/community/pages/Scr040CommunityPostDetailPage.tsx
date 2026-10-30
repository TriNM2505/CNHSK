import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ToastProvider } from '@/shared/ui/Toast'
import { useToast } from '@/shared/ui/useToast'
import { createComment, deleteComment, listPostComments, replyToComment, updateComment } from '../api/comments'
import { listPostCommentsForModeration, moderateComment } from '../api/moderation'
import { getPublishedPost, getViewerState, likePost, unlikePost } from '../api/posts'
import { reportContent } from '../api/reports'
import { canEditComment, canModerateComment, COMMENT_EDIT_WINDOW_MS } from '../comment-permissions'
import { CommentForm } from '../components/CommentForm'
import { CommentModerationForm, type ModerationDraft } from '../components/CommentModerationForm'
import { CommentTree } from '../components/CommentTree'
import type { DiscussionSelection } from '../components/CommentItem'
import { CommunityPostDetailView } from '../components/CommunityPostDetailView'
import { InteractionError } from '../components/InteractionError'
import { ReportForm, type ReportDraft } from '../components/ReportForm'
import { validateText } from '../interaction-validation'
import { parsePostId } from '../postForm'
import type { Comment, LikeState, ModerationComment, PostDetail, ViewerState } from '../types'
import { useSessionAction } from '../use-session-action'

function NotFound() {
  return <main className="mx-auto max-w-reading p-6"><h1>Không tìm thấy bài viết</h1><Link to="/community">Về cộng đồng</Link></main>
}

type DiscussionProps = { postId: number; user: CurrentUser | null; active: boolean; refreshPost: () => void }
type QueryProps = DiscussionProps & { session: ReturnType<typeof useDiscussionSession> }
type ThreadProps = QueryProps & { comments: readonly (Comment | ModerationComment)[]; state: 'loading' | 'empty' | 'error' | 'ready'; error: unknown; reload: () => void; moderator: boolean }

function useDiscussionSession() {
  const [selection, setSelection] = useState<DiscussionSelection>()
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [validation, setValidation] = useState<string>()
  const [lockedEdits, setLockedEdits] = useState<ReadonlySet<number>>(new Set())
  const [reportDraft, setReportDraft] = useState<ReportDraft>({ reason: '', detail: '' })
  const [moderationDraft, setModerationDraft] = useState<ModerationDraft>({ reason: '', confirming: false })
  const [revision, setRevision] = useState(0)
  const action = useSessionAction()
  return { selection, setSelection, drafts, setDrafts, error, setError, validation, setValidation, lockedEdits, setLockedEdits, reportDraft, setReportDraft, moderationDraft, setModerationDraft, revision, reload: () => setRevision((value) => value + 1), action }
}

function DiscussionSession(props: DiscussionProps & { moderator: boolean }) {
  const session = useDiscussionSession()
  // Draft state survives a public refresh; query lifetimes never use cached detail as permission.
  if (!props.active) return null
  return props.moderator ? <ModeratorDiscussion {...props} session={session} /> : <PublicDiscussion {...props} session={session} />
}

function DiscussionController({ postId, user, active, refreshPost, comments, state, error: queryError, reload, moderator, session }: ThreadProps) {
  const learner = Boolean(user?.roles.includes('LEARNER'))
  const verified = Boolean(user?.emailVerified)
  const { selection, setSelection, drafts, setDrafts, error, setError, validation, setValidation, lockedEdits, setLockedEdits, reportDraft, setReportDraft, moderationDraft, setModerationDraft, action } = session
  const [now, setNow] = useState(Date.now)
  const { showToast } = useToast()
  useEffect(() => {
    const update = () => setNow(Date.now())
    const expirations = comments.map((comment) => Date.parse(comment.createdAt) + COMMENT_EDIT_WINDOW_MS).filter((time) => time > now && Number.isFinite(time))
    const timer = expirations.length ? window.setTimeout(update, Math.min(...expirations) - now) : undefined
    window.addEventListener('focus', update)
    return () => { window.clearTimeout(timer); window.removeEventListener('focus', update) }
  }, [comments, now])
  const ready = active && state === 'ready'
  const disabled = !ready || action.busy
  const selected = selection && selection.kind !== 'new' ? comments.find(({ id }) => id === selection.comment.id) : undefined
  const draftKey = selection ? selection.kind === 'new' ? 'new' : `${selection.kind}:${selection.comment.id}` : ''
  const choose = (next: DiscussionSelection) => {
    setSelection(next); setError(undefined); setValidation(undefined)
    if (next.kind === 'edit') setDrafts((current) => ({ ...current, [`edit:${next.comment.id}`]: current[`edit:${next.comment.id}`] ?? next.comment.content ?? '' }))
    if (next.kind === 'report') setReportDraft({ reason: '', detail: '' })
    if (next.kind === 'moderate') setModerationDraft({ reason: '', confirming: false })
  }
  const fail = (caught: unknown) => {
    setError(caught)
    if (caught instanceof ApiError && caught.status === 409 && selection?.kind === 'edit') setLockedEdits((current) => new Set([...current, selection.comment.id]))
    if (caught instanceof ApiError && caught.errorCode === 'POST_NOT_FOUND') refreshPost()
    else if (caught instanceof ApiError && (caught.status === 409 || caught.status === 404)) reload()
  }
  const succeed = (message: string) => {
    setDrafts((current) => ({ ...current, [draftKey]: '' })); setSelection(undefined); setError(undefined)
    // Reconfirm the public post before remounting the appropriate comments query.
    showToast({ message, tone: 'success' }); refreshPost()
  }
  const editAllowed = selected && canEditComment(selected, user?.id, now) && !lockedEdits.has(selected.id)
  const commentAllowed = learner && verified && !disabled && selection && (selection.kind === 'new' || (Boolean(selected) && (selection.kind === 'reply' || (selection.kind === 'edit' && editAllowed))))
  const submitComment = () => {
    if (!commentAllowed || !selection) return
    if (selection.kind === 'edit' && selected && !canEditComment(selected, user?.id, Date.now())) { setNow(Date.now()); return }
    const content = drafts[draftKey] ?? ''
    const invalid = validateText(content, 5000); setValidation(invalid)
    if (invalid) return
    const body = { content: content.trim() }
    const operation = selection.kind === 'new' ? () => createComment(postId, body) : selection.kind === 'reply' ? () => replyToComment(selection.comment.id, body) : () => updateComment(selection.comment.id, body)
    action.run(operation, () => succeed('Đã lưu bình luận'), fail)
  }
  return <section aria-label="Thảo luận" className="mt-6">
    <h2 className="text-h2">Bình luận</h2>
    {!user ? <Link to="/login">Đăng nhập để bình luận</Link> : learner && !verified ? <p>Xác minh email để bình luận và báo cáo. <Link to="/verify-email">Xác minh email</Link></p> : learner ? <button disabled={disabled} onClick={() => choose({ kind: 'new' })} type="button">Viết bình luận</button> : null}
    {!active || state === 'loading' ? <p role="status">Đang tải bình luận…</p> : state === 'error' ? <><InteractionError error={queryError} /><button type="button" onClick={reload}>Thử lại bình luận</button></> : <CommentTree postId={postId} comments={comments} actions={{ userId: user?.id, learner, verified, moderator, guest: !user, disabled, now, lockedEdits, onSelect: choose }} onRetry={reload} />}
    <InteractionError error={error} />
    {selection && ['new', 'reply', 'edit'].includes(selection.kind) ? <>
      {selection.kind === 'edit' && !editAllowed ? <p role="status">Bình luận không còn có thể sửa. Nội dung đang nhập được giữ lại.</p> : null}
      <CommentForm value={drafts[draftKey] ?? ''} onChange={(value) => setDrafts((current) => ({ ...current, [draftKey]: value }))} onSubmit={submitComment} onCancel={() => setSelection(undefined)} disabled={!commentAllowed} label={selection.kind === 'edit' ? 'Sửa bình luận' : selection.kind === 'reply' ? 'Trả lời bình luận' : 'Nội dung bình luận'} validation={validation} />
    </> : null}
    {selection?.kind === 'delete' ? <section aria-label="Xác nhận xoá bình luận"><p>Xoá bình luận này? Các trả lời được giữ nguyên.</p><button disabled={disabled || !verified || !learner || selected?.author?.id !== user?.id} type="button" onClick={() => action.run(() => deleteComment(selection.comment.id), () => succeed('Đã xoá bình luận'), fail)}>Xác nhận xoá</button><button disabled={action.busy} type="button" onClick={() => setSelection(undefined)}>Huỷ</button></section> : null}
    {selection?.kind === 'report' ? <ReportForm key={draftKey} targetType="COMMENT" targetId={selection.comment.id} draft={reportDraft} onDraftChange={setReportDraft} disabled={disabled || !learner || !verified || selected?.status !== 'VISIBLE' || selected.author?.id === user?.id} onSubmit={(body) => action.run(() => reportContent(body), () => succeed('Đã nhận báo cáo'), fail)} onCancel={() => setSelection(undefined)} /> : null}
    {selection?.kind === 'moderate' ? <CommentModerationForm key={`${draftKey}:${selection.action}`} action={selection.action} draft={moderationDraft} onDraftChange={setModerationDraft} disabled={disabled || !moderator || !selected || !('moderationReason' in selected) || !canModerateComment(selected, user?.id ?? -1, selection.action)} onSubmit={(body) => action.run(() => moderateComment(selection.comment.id, body), () => succeed('Đã kiểm duyệt bình luận'), fail)} onCancel={() => setSelection(undefined)} /> : null}
  </section>
}

function PublicDiscussion(props: QueryProps) {
  const query = useApiQuery((signal) => listPostComments(props.postId, signal), [props.postId, props.session.revision])
  return <DiscussionController {...props} comments={query.data ?? []} state={query.state} error={query.error} reload={props.session.reload} moderator={false} />
}
function ModeratorDiscussion(props: QueryProps) {
  const query = useApiQuery((signal) => listPostCommentsForModeration(props.postId, signal), [props.postId, props.session.revision])
  return <DiscussionController {...props} comments={query.data ?? []} state={query.state} error={query.error} reload={props.session.reload} moderator />
}

function ViewerStateQuery({ postId, revision, children }: { postId: number; revision: number; children: (query: ReturnType<typeof useApiQuery<ViewerState>>) => ReactNode }) {
  const query = useApiQuery((signal) => getViewerState(postId, signal), [postId, revision])
  return children(query)
}

function LearnerInteractions({ post, user, active, onCount, refreshPost }: { post: PostDetail; user: CurrentUser; active: boolean; onCount: (count: number) => void; refreshPost: () => void }) {
  const [confirmed, setConfirmed] = useState<LikeState>()
  const [reported, setReported] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportDraft, setReportDraft] = useState<ReportDraft>({ reason: '', detail: '' })
  const [error, setError] = useState<unknown>()
  const [revision, setRevision] = useState(0)
  const refreshViewer = () => setRevision((value) => value + 1)
  const like = useSessionAction<LikeState>()
  const report = useSessionAction()
  const { showToast } = useToast()
  const fail = (caught: unknown) => {
    setError(caught)
    if (caught instanceof ApiError && caught.errorCode === 'POST_NOT_FOUND') refreshPost()
  }
  const failReport = (caught: unknown) => {
    fail(caught)
    if (caught instanceof ApiError && caught.status === 404 && caught.errorCode === 'TARGET_NOT_FOUND') refreshPost()
  }
  if (!active) return null
  return <ViewerStateQuery postId={post.id} revision={revision}>{(query) => {
    const liked = confirmed?.liked ?? query.data?.liked
    const trusted = Boolean(confirmed) || query.state === 'ready'
    return <section aria-label="Tương tác bài viết">
    <button type="button" disabled={!active || !trusted || like.busy} aria-pressed={trusted ? liked : undefined} onClick={() => like.run(() => liked ? unlikePost(post.id) : likePost(post.id), (result) => { setConfirmed(result); onCount(result.likeCount); setError(undefined) }, fail)}>{liked ? 'Bỏ thích' : 'Thích'}</button>
    {query.state === 'loading' ? <p role="status">Đang tải trạng thái người xem…</p> : null}
    {query.state === 'error' ? <><InteractionError error={query.error} /><button type="button" onClick={refreshViewer}>Thử lại trạng thái người xem</button></> : null}
    {reported || query.data?.reported ? <p>Đã báo cáo bài viết</p> : user.emailVerified && post.author.id !== user.id ? <button disabled={!active || query.state !== 'ready' || report.busy} type="button" onClick={() => setReportOpen(true)}>Báo cáo bài viết</button> : null}
    <InteractionError error={error} />
    {reportOpen ? <ReportForm targetType="POST" targetId={post.id} draft={reportDraft} onDraftChange={setReportDraft} disabled={!active || report.busy || !user.emailVerified} onCancel={() => setReportOpen(false)} onSubmit={(body) => report.run(() => reportContent(body), () => { setReported(true); setReportOpen(false); showToast({ message: 'Đã nhận báo cáo', tone: 'success' }); refreshViewer() }, failReport)} /> : null}
  </section>
  }}</ViewerStateQuery>
}

function PublishedPost({ id }: { id: number }) {
  const [requestVersion, setRequestVersion] = useState(0)
  const query = useApiQuery(async (signal) => ({ post: await getPublishedPost(id, signal), version: requestVersion }), [id, requestVersion])
  const refreshPost = () => setRequestVersion((value) => value + 1)
  const auth = useCurrentUser()
  const lastPost = useRef<PostDetail>()
  const session = useRef({ identity: '', generation: 0 })
  const [count, setCount] = useState<{ scope: string; value: number }>()
  // Invalidate immediately on refresh, even if React batches a fast loading/ready response.
  const post = query.state === 'ready' && query.data?.version === requestVersion ? query.data.post : undefined
  if (post) lastPost.current = post
  // Retained metadata keeps draft owners mounted, never queries or visible projections.
  const sessionPost = post ?? lastPost.current
  const user = auth.status === 'authenticated' ? auth.user : null
  const identity = `${auth.status}:${user?.id ?? ''}:${user?.roles.join(',') ?? ''}:${user?.emailVerified ?? ''}`
  if (session.current.identity !== identity) session.current = { identity, generation: session.current.generation + 1 }
  const scope = `${session.current.generation}:${identity}`
  if (query.error?.status === 404) return <NotFound />
  const active = Boolean(post)
  return <main className="mx-auto max-w-reading p-4 sm:p-6">
    <Link to="/community">Về cộng đồng</Link>
    {query.state !== 'error' && !post ? <p role="status">Đang tải bài viết…</p> : query.state === 'error' ? <><InteractionError error={query.error} /><button type="button" onClick={refreshPost}>Thử lại bài viết</button></> : post ? <CommunityPostDetailView post={post} likeCount={count?.scope === scope ? count.value : post.likeCount} canEdit={Boolean(user?.roles.includes('LEARNER') && user.id === post.author.id)} /> : null}
    {sessionPost ? <div key={scope}>
      {auth.status === 'loading' ? <p role="status">Đang kiểm tra phiên đăng nhập…</p> : auth.status === 'error' ? <><InteractionError error={auth.error} /><button type="button" onClick={() => void auth.reloadCurrentUser()}>Thử lại phiên đăng nhập</button></> : <>
        {user?.roles.includes('LEARNER') ? <LearnerInteractions post={sessionPost} user={user} active={active} onCount={(value) => setCount({ scope, value })} refreshPost={refreshPost} /> : !user && active ? <><Link to="/login">Thích</Link> · <Link to="/login">Báo cáo bài viết</Link></> : null}
        <DiscussionSession postId={id} user={user} active={active} refreshPost={refreshPost} moderator={Boolean(user?.roles.includes('MODERATOR'))} />
      </>}
    </div> : null}
  </main>
}

export default function Scr040CommunityPostDetailPage() {
  const { id: rawId } = useParams()
  const id = parsePostId(rawId)
  if (id === undefined) return <NotFound />
  return <ToastProvider key={id}><PublishedPost id={id} /></ToastProvider>
}
