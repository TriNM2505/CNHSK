import type { FormEvent } from 'react'
import { Button } from '@/shared/ui/Button'
import { FormField } from '@/shared/ui/FormField'

import { POST_CATEGORIES, POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { OwnPost, PostCategory } from '../types'
import type { PostFormErrors, PostFormValues } from '../postForm'

export type EditorMode = 'create' | 'edit'

type Props = {
  busy: boolean
  errors: PostFormErrors
  formError?: string
  verificationRequired?: boolean
  locked: boolean
  mode: EditorMode
  onChange: (field: keyof PostFormValues, value: string) => void
  onSubmit: (submit: boolean) => void
  post?: OwnPost
  values: PostFormValues
}

export function PostEditorView({ busy, errors, formError, locked, mode, onChange, onSubmit, post, values, verificationRequired }: Props) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>, submit: boolean) => {
    event.preventDefault()
    onSubmit(submit)
  }

  if (post?.status === 'HIDDEN') {
    return <main className="mx-auto max-w-reading p-4 sm:p-6">
      <h1 className="text-h1-sm text-ink sm:text-h1">Chỉnh sửa bài viết</h1>
      <p className="mt-4" role="status">{POST_STATUS_LABELS.HIDDEN}</p>
      {post.reviewReason ? <p className="mt-3 whitespace-pre-wrap">{post.reviewReason}</p> : null}
      <Button className="mt-5" to="/community?tab=mine" variant="outline">Về Bài viết của tôi</Button>
    </main>
  }

  return <main className="mx-auto max-w-reading p-4 sm:p-6">
    <h1 className="text-h1-sm text-ink sm:text-h1">{mode === 'create' ? 'Viết bài cộng đồng' : 'Chỉnh sửa bài viết'}</h1>
    {post?.status === 'APPROVED' ? <p className="mt-4 rounded-md border border-warning bg-warning-bg p-3" role="note">Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại</p> : null}
    {post?.status === 'REJECTED' && post.reviewReason ? <p className="mt-4 whitespace-pre-wrap" role="note">Lý do từ chối: {post.reviewReason}</p> : null}
    {formError ? <p className="mt-4 rounded-md border border-danger bg-danger-bg p-3" role="alert">{formError}</p> : null}
    {verificationRequired ? <Button className="mt-3" to="/verify-email" variant="outline">Xác minh email</Button> : null}
    <form className="mt-5 space-y-4" onSubmit={(event) => handleSubmit(event, true)}>
      <FormField error={errors.title} htmlFor="post-title" label="Tiêu đề" required hint={`${values.title.trim().length}/200`}>
        <input className="min-h-control w-full rounded-sm border border-line bg-surface px-3" disabled={busy || locked} id="post-title" onChange={(event) => onChange('title', event.target.value)} value={values.title} />
      </FormField>
      <FormField error={errors.category} htmlFor="post-category" label="Danh mục" required>
        <select className="min-h-control w-full rounded-sm border border-line bg-surface px-3" disabled={busy || locked} id="post-category" onChange={(event) => onChange('category', event.target.value)} value={values.category}>
          <option value="">Chọn danh mục</option>
          {POST_CATEGORIES.map((category: PostCategory) => <option key={category} value={category}>{POST_CATEGORY_LABELS[category]}</option>)}
        </select>
      </FormField>
      <FormField error={errors.content} htmlFor="post-content" label="Nội dung" required hint={`${values.content.trim().length}/50.000`}>
        <textarea className="min-h-64 w-full resize-y rounded-sm border border-line bg-surface px-3 py-2" disabled={busy || locked} id="post-content" onChange={(event) => onChange('content', event.target.value)} value={values.content} />
      </FormField>
      {locked ? <Button className="mb-3" to="/community?tab=mine" variant="outline">Về Bài viết của tôi</Button> : null}
      <div className="flex flex-wrap gap-3">
        <Button disabled={busy || locked} onClick={() => onSubmit(false)} type="button" variant="outline">Lưu nháp</Button>
        <Button disabled={busy || locked} loading={busy} type="submit">{mode === 'edit' ? 'Gửi duyệt' : 'Gửi duyệt'}</Button>
      </div>
    </form>
  </main>
}
