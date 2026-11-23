import { X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { Button, Input, Textarea } from '@/shared/ui'

import { createPost } from '../api/posts'
import { POST_CATEGORIES, POST_CATEGORY_LABELS } from '../labels'
import { toPostWrite, validatePostForm, type PostFormErrors, type PostFormValues } from '../postForm'
import type { MediaItem } from '../types'
import { MediaPicker } from './MediaPicker'

const EMPTY: PostFormValues = { category: 'QUESTION', content: '', title: '' }

export type PostComposerDialogProps = {
  authorName: string
  onClose: () => void
  /** Called after the server accepted the post; `submitted` is false for a saved draft. */
  onPosted: (submitted: boolean) => void
}

/**
 * "Tạo bài viết" over the feed, the way a social network does it: topic, title, text and attachments in one dialog,
 * and the reader stays where they were on the page.
 */
export function PostComposerDialog({ authorName, onClose, onPosted }: PostComposerDialogProps) {
  const [values, setValues] = useState<PostFormValues>(EMPTY)
  const [media, setMedia] = useState<MediaItem[]>([])
  const [errors, setErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string>()
  const [needsVerification, setNeedsVerification] = useState(false)
  const [busy, setBusy] = useState(false)
  const [confirmingClose, setConfirmingClose] = useState(false)
  const titleInput = useRef<HTMLInputElement>(null)
  const dirty = Boolean(values.title.trim() || values.content.trim() || media.length > 0)

  useEffect(() => {
    titleInput.current?.focus()
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [])

  function requestClose() {
    if (busy) return
    if (dirty) setConfirmingClose(true)
    else onClose()
  }

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') requestClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  })

  function change(field: keyof PostFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function send(submit: boolean) {
    const found = validatePostForm(values)
    setErrors(found)
    const body = toPostWrite(values, submit)
    if (!body || Object.keys(found).length > 0) return
    setBusy(true)
    setFormError(undefined)
    setNeedsVerification(false)
    try {
      await createPost({ ...body, media })
      onPosted(submit)
    } catch (caught) {
      if (caught instanceof ApiError && caught.errorCode === 'ACCOUNT_UNVERIFIED') setNeedsVerification(true)
      setFormError(caught instanceof Error ? caught.message : 'Chưa đăng được bài, thử lại.')
      setBusy(false)
    }
  }

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void send(true)
  }

  return (
    <div className="fixed inset-0 z-overlay flex items-end justify-center bg-ink/50 sm:items-center sm:p-6">
      <button aria-label="Đóng" className="absolute inset-0 cursor-default" onClick={requestClose} tabIndex={-1} type="button" />
      <section
        aria-labelledby="composer-title"
        aria-modal="true"
        className="relative flex max-h-[100dvh] w-full min-w-0 flex-col overflow-hidden bg-surface shadow-overlay sm:max-h-[calc(100dvh-3rem)] sm:max-w-reading sm:rounded-lg"
        role="dialog"
      >
        <header className="relative flex items-center justify-center border-b border-line px-12 py-4">
          <h2 className="text-h3 text-ink" id="composer-title">
            Tạo bài viết
          </h2>
          <button
            aria-label="Đóng"
            className="absolute right-3 flex size-10 items-center justify-center rounded-full bg-canvas text-ink-secondary hover:bg-line"
            onClick={requestClose}
            type="button"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </header>

        <form className="flex min-h-0 flex-1 flex-col" noValidate onSubmit={onSubmit}>
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-4">
            <div className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-body font-semibold text-brand-700"
              >
                {authorName.trim().charAt(0).toLocaleUpperCase('vi') || '?'}
              </span>
              <div className="min-w-0">
                <p className="truncate text-label text-ink">{authorName}</p>
                <p className="text-caption text-ink-muted">Bài sẽ hiện sau khi kiểm duyệt viên duyệt</p>
              </div>
            </div>

            <fieldset>
              <legend className="mb-2 text-label text-ink">Chủ đề</legend>
              <div className="flex flex-wrap gap-2">
                {POST_CATEGORIES.map((category) => (
                  <button
                    aria-pressed={values.category === category}
                    className={cn(
                      'min-h-9 rounded-pill border px-3 text-body-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                      values.category === category
                        ? 'border-brand-600 bg-brand-600 text-surface'
                        : 'border-line bg-surface text-ink hover:border-brand-600',
                    )}
                    disabled={busy}
                    key={category}
                    onClick={() => change('category', category)}
                    type="button"
                  >
                    {POST_CATEGORY_LABELS[category]}
                  </button>
                ))}
              </div>
              {errors.category ? <p className="mt-1 text-caption text-danger">{errors.category}</p> : null}
            </fieldset>

            <div>
              <label className="sr-only" htmlFor="composer-title-input">
                Tiêu đề
              </label>
              <Input
                aria-invalid={Boolean(errors.title)}
                className="text-h3"
                disabled={busy}
                id="composer-title-input"
                maxLength={200}
                onChange={(event) => change('title', event.target.value)}
                placeholder="Tiêu đề bài viết"
                ref={titleInput}
                value={values.title}
              />
              {errors.title ? <p className="mt-1 text-caption text-danger">{errors.title}</p> : null}
            </div>

            <div>
              <label className="sr-only" htmlFor="composer-content">
                Nội dung
              </label>
              <Textarea
                aria-invalid={Boolean(errors.content)}
                className="min-h-40 border-0 px-0 text-body shadow-none focus:ring-0"
                disabled={busy}
                id="composer-content"
                onChange={(event) => change('content', event.target.value)}
                placeholder={`${authorName}, bạn đang thắc mắc điều gì?`}
                value={values.content}
              />
              {errors.content ? <p className="mt-1 text-caption text-danger">{errors.content}</p> : null}
            </div>

            <div className="rounded-md border border-line p-3">
              <p className="mb-2 text-label text-ink">Thêm vào bài viết</p>
              <MediaPicker disabled={busy} media={media} onChange={setMedia} />
            </div>

            {formError ? (
              <div className="space-y-1 text-body-sm text-danger" role="alert">
                <p>{formError}</p>
                {needsVerification ? (
                  <Link className="font-semibold underline" to="/verify-email?redirect=%2Fcommunity">
                    Xác minh email
                  </Link>
                ) : null}
              </div>
            ) : null}
          </div>

          <footer className="space-y-2 border-t border-line px-5 py-3">
            {confirmingClose ? (
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-warning-bg p-3" role="alert">
                <p className="text-body-sm text-ink">Bỏ bài đang viết?</p>
                <div className="flex gap-2">
                  <Button onClick={() => setConfirmingClose(false)} size="sm" variant="ghost">
                    Viết tiếp
                  </Button>
                  <Button onClick={onClose} size="sm" variant="danger">
                    Bỏ bài
                  </Button>
                </div>
              </div>
            ) : null}
            <div className="flex gap-2">
              <Button disabled={busy} onClick={() => void send(false)} variant="outline">
                Lưu nháp
              </Button>
              <Button className="flex-1" disabled={busy || !values.title.trim() || !values.content.trim()} type="submit">
                {busy ? 'Đang đăng…' : 'Đăng bài'}
              </Button>
            </div>
          </footer>
        </form>
      </section>
    </div>
  )
}
