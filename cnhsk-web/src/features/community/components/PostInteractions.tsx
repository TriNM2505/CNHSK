import { Heart, MessageCircle, SendHorizontal } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

import { createComment, listPostComments, replyToComment } from '../api/comments'
import { likePost, unlikePost } from '../api/posts'
import type { Comment } from '../types'

const SHOWN_THREADS = 3

export type PostInteractionsProps = {
  commentCount: number
  /** Learners may like and comment; anyone else is sent to sign in. */
  canInteract: boolean
  likeCount: number
  liked: boolean
  postId: number
  viewerName?: string
}

type Thread = { root: Comment; replies: Comment[] }

function Initial({ name, small = false }: { name: string; small?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full bg-brand-50 text-label text-brand-700',
        small ? 'size-7' : 'size-8',
      )}
    >
      {name.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

/** Groups comments under their top-level comment; deeper replies stay with the thread they belong to. */
function toThreads(comments: readonly Comment[]): Thread[] {
  const byId = new Map(comments.map((comment) => [comment.id, comment]))
  const rootOf = (comment: Comment): Comment => {
    let current = comment
    for (let guard = 0; current.parentId !== null && guard < 10; guard++) {
      const parent = byId.get(current.parentId)
      if (!parent) break
      current = parent
    }
    return current
  }
  const threads = new Map<number, Thread>()
  for (const comment of comments) {
    const root = rootOf(comment)
    const thread = threads.get(root.id) ?? { root, replies: [] }
    if (comment.id !== root.id) thread.replies.push(comment)
    threads.set(root.id, thread)
  }
  return [...threads.values()]
}

function Bubble({ comment, onReply }: { comment: Comment; onReply?: () => void }) {
  const name = comment.author?.displayName ?? 'Người dùng đã rời'
  return (
    <div className="min-w-0">
      <div className="rounded-md bg-canvas px-3 py-2">
        <p className="text-label text-ink">{name}</p>
        <p className="whitespace-pre-wrap break-words text-body-sm text-ink">{comment.content}</p>
      </div>
      {onReply ? (
        <button className="ml-3 mt-0.5 text-caption font-semibold text-ink-secondary hover:underline" onClick={onReply} type="button">
          Phản hồi
        </button>
      ) : null}
    </div>
  )
}

/** Like, comment and reply right on a feed card, the way a social feed works; the full page has every comment. */
export function PostInteractions(props: PostInteractionsProps) {
  const navigate = useNavigate()
  const [liked, setLiked] = useState(props.liked)
  const [likeCount, setLikeCount] = useState(props.likeCount)
  const [commentCount, setCommentCount] = useState(props.commentCount)
  const [open, setOpen] = useState(false)
  const [comments, setComments] = useState<Comment[]>()
  const [draft, setDraft] = useState('')
  const [replyTo, setReplyTo] = useState<Comment>()
  const [replyDraft, setReplyDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const replyInput = useRef<HTMLInputElement>(null)

  useEffect(() => setLiked(props.liked), [props.liked])

  useEffect(() => {
    if (!open || comments) return
    const controller = new AbortController()
    listPostComments(props.postId, controller.signal)
      .then((found) =>
        setComments(
          found
            .filter((comment) => comment.status === 'VISIBLE')
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
        ),
      )
      .catch(() => setError('Không tải được bình luận.'))
    return () => controller.abort()
  }, [comments, open, props.postId])

  useEffect(() => {
    if (replyTo) replyInput.current?.focus()
  }, [replyTo])

  const threads = useMemo(() => toThreads(comments ?? []), [comments])
  const shown = threads.slice(-SHOWN_THREADS)

  function requireSignIn() {
    navigate(`/login?redirect=${encodeURIComponent(`/community/posts/${props.postId}`)}`)
  }

  async function toggleLike() {
    if (!props.canInteract) return requireSignIn()
    const next = !liked
    setLiked(next)
    setLikeCount((count) => count + (next ? 1 : -1))
    try {
      const state = next ? await likePost(props.postId) : await unlikePost(props.postId)
      setLiked(state.liked)
      setLikeCount(state.likeCount)
    } catch {
      setLiked(!next)
      setLikeCount((count) => count + (next ? -1 : 1))
      setError('Chưa lưu được lượt thích, thử lại.')
    }
  }

  async function send(content: string, parent?: Comment) {
    const text = content.trim()
    if (!text || busy) return false
    setBusy(true)
    setError(undefined)
    try {
      const created = parent
        ? await replyToComment(parent.id, { content: text })
        : await createComment(props.postId, { content: text })
      setComments((current) => [...(current ?? []), created])
      setCommentCount((count) => count + 1)
      return true
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Chưa gửi được bình luận.')
      return false
    } finally {
      setBusy(false)
    }
  }

  async function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (await send(draft)) setDraft('')
  }

  async function submitReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (replyTo && (await send(replyDraft, replyTo))) {
      setReplyDraft('')
      setReplyTo(undefined)
    }
  }

  function startReply(target: Comment) {
    if (!props.canInteract) return requireSignIn()
    setReplyTo(target)
    setReplyDraft('')
  }

  const actionClass =
    'flex min-h-10 flex-1 items-center justify-center gap-2 rounded-sm text-label transition-colors hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'
  const inputClass =
    'min-h-10 min-w-0 flex-1 rounded-pill border border-line bg-canvas px-4 text-body focus:border-brand-600 focus:outline-none'

  return (
    <div className="mt-3">
      <div className="flex items-center justify-between pb-2 text-caption text-ink-muted">
        <span>{likeCount > 0 ? `${likeCount} lượt thích` : 'Chưa có lượt thích'}</span>
        <button className="hover:underline" onClick={() => setOpen((value) => !value)} type="button">
          {commentCount} bình luận
        </button>
      </div>
      <div className={cn('flex gap-1 border-t border-line pt-1', open && 'border-b pb-1')}>
        <button
          aria-pressed={liked}
          className={cn(actionClass, liked ? 'text-brand-600' : 'text-ink-secondary')}
          onClick={() => void toggleLike()}
          type="button"
        >
          <Heart aria-hidden="true" className={cn('size-5', liked && 'fill-brand-600')} />
          Thích
        </button>
        <button
          aria-expanded={open}
          className={cn(actionClass, 'text-ink-secondary')}
          onClick={() => (props.canInteract || open ? setOpen((value) => !value) : requireSignIn())}
          type="button"
        >
          <MessageCircle aria-hidden="true" className="size-5" />
          Bình luận
        </button>
      </div>

      {open ? (
        <div className="space-y-3 pt-3">
          {threads.length > shown.length ? (
            <Link className="block text-label text-ink-secondary hover:underline" to={`/community/posts/${props.postId}`}>
              Xem tất cả {commentCount} bình luận
            </Link>
          ) : null}
          {comments === undefined && !error ? <p className="text-caption text-ink-muted">Đang tải bình luận…</p> : null}

          {shown.map(({ replies, root }) => {
            const replying = replyTo !== undefined && (replyTo.id === root.id || replies.some((reply) => reply.id === replyTo.id))
            return (
              <div className="space-y-2" key={root.id}>
                <div className="flex gap-2">
                  <Initial name={root.author?.displayName ?? '?'} />
                  <Bubble comment={root} onReply={() => startReply(root)} />
                </div>
                {replies.length > 0 || replying ? (
                  <div className="ml-10 space-y-2 border-l-2 border-line pl-3">
                    {replies.map((reply) => (
                      <div className="flex gap-2" key={reply.id}>
                        <Initial name={reply.author?.displayName ?? '?'} small />
                        <Bubble comment={reply} onReply={() => startReply(reply)} />
                      </div>
                    ))}
                    {replying && replyTo ? (
                      <form className="flex items-center gap-2" onSubmit={(event) => void submitReply(event)}>
                        <Initial name={props.viewerName ?? 'B'} small />
                        <label className="sr-only" htmlFor={`reply-${props.postId}-${root.id}`}>
                          Phản hồi {replyTo.author?.displayName ?? ''}
                        </label>
                        <input
                          className={inputClass}
                          disabled={busy}
                          id={`reply-${props.postId}-${root.id}`}
                          maxLength={2000}
                          onChange={(event) => setReplyDraft(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === 'Escape') setReplyTo(undefined)
                          }}
                          placeholder={`Phản hồi ${replyTo.author?.displayName ?? 'bình luận'}…`}
                          ref={replyInput}
                          value={replyDraft}
                        />
                        <button
                          aria-label="Gửi phản hồi"
                          className="flex size-10 shrink-0 items-center justify-center rounded-full text-brand-600 hover:bg-brand-50 disabled:text-ink-muted"
                          disabled={busy || !replyDraft.trim()}
                          type="submit"
                        >
                          <SendHorizontal aria-hidden="true" className="size-5" />
                        </button>
                      </form>
                    ) : null}
                  </div>
                ) : null}
              </div>
            )
          })}

          {props.canInteract ? (
            <form className="flex items-center gap-2" onSubmit={(event) => void submitComment(event)}>
              <Initial name={props.viewerName ?? 'B'} />
              <label className="sr-only" htmlFor={`comment-${props.postId}`}>
                Viết bình luận
              </label>
              <input
                className={inputClass}
                disabled={busy}
                id={`comment-${props.postId}`}
                maxLength={2000}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Viết bình luận…"
                value={draft}
              />
              <button
                aria-label="Gửi bình luận"
                className="flex size-10 shrink-0 items-center justify-center rounded-full text-brand-600 hover:bg-brand-50 disabled:text-ink-muted"
                disabled={busy || !draft.trim()}
                type="submit"
              >
                <SendHorizontal aria-hidden="true" className="size-5" />
              </button>
            </form>
          ) : null}
        </div>
      ) : null}
      {error ? (
        <p className="pt-2 text-caption text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
