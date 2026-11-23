import { Heart, MessageCircle, SendHorizontal } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

import { createComment, listPostComments } from '../api/comments'
import { likePost, unlikePost } from '../api/posts'
import type { Comment } from '../types'

const SHOWN_COMMENTS = 3

export type PostInteractionsProps = {
  commentCount: number
  /** Learners may like and comment; anyone else is sent to sign in. */
  canInteract: boolean
  likeCount: number
  liked: boolean
  postId: number
  viewerName?: string
}

function Initial({ name }: { name: string }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-label text-brand-700"
    >
      {name.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
    </span>
  )
}

/** Like and comment right on a feed card, the way a social feed works; the full thread stays on the post page. */
export function PostInteractions(props: PostInteractionsProps) {
  const navigate = useNavigate()
  const [liked, setLiked] = useState(props.liked)
  const [likeCount, setLikeCount] = useState(props.likeCount)
  const [commentCount, setCommentCount] = useState(props.commentCount)
  const [open, setOpen] = useState(false)
  const [comments, setComments] = useState<Comment[]>()
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => setLiked(props.liked), [props.liked])

  useEffect(() => {
    if (!open || comments) return
    const controller = new AbortController()
    listPostComments(props.postId, controller.signal)
      .then((found) => setComments(found.filter((comment) => comment.status === 'VISIBLE' && comment.depth === 0)))
      .catch(() => setError('Không tải được bình luận.'))
    return () => controller.abort()
  }, [comments, open, props.postId])

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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || busy) return
    setBusy(true)
    setError(undefined)
    try {
      const created = await createComment(props.postId, { content })
      setComments((current) => [...(current ?? []), created])
      setCommentCount((count) => count + 1)
      setDraft('')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Chưa gửi được bình luận.')
    } finally {
      setBusy(false)
    }
  }

  const shown = (comments ?? []).slice(-SHOWN_COMMENTS)
  const actionClass =
    'flex min-h-10 flex-1 items-center justify-center gap-2 rounded-sm text-label transition-colors hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

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
          {commentCount > shown.length && comments ? (
            <Link className="block text-label text-ink-secondary hover:underline" to={`/community/posts/${props.postId}`}>
              Xem tất cả {commentCount} bình luận
            </Link>
          ) : null}
          {comments === undefined && !error ? <p className="text-caption text-ink-muted">Đang tải bình luận…</p> : null}
          {shown.map((comment) => {
            const name = comment.author?.displayName ?? 'Người dùng đã rời'
            return (
              <div className="flex gap-2" key={comment.id}>
                <Initial name={name} />
                <div className="min-w-0 rounded-md bg-canvas px-3 py-2">
                  <p className="text-label text-ink">{name}</p>
                  <p className="whitespace-pre-wrap break-words text-body-sm text-ink">{comment.content}</p>
                </div>
              </div>
            )
          })}
          {props.canInteract ? (
            <form className="flex items-center gap-2" onSubmit={(event) => void submit(event)}>
              <Initial name={props.viewerName ?? 'B'} />
              <label className="sr-only" htmlFor={`comment-${props.postId}`}>
                Viết bình luận
              </label>
              <input
                className="min-h-10 min-w-0 flex-1 rounded-pill border border-line bg-canvas px-4 text-body focus:border-brand-600 focus:outline-none"
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
