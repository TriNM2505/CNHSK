import type { FormEvent } from 'react'

import {
  Button,
  Card,
  FormField,
  Input,
  PageBanner,
  Select,
  StatusBadge,
  Textarea,
} from '@/shared/ui'

import { POST_CATEGORIES, POST_CATEGORY_LABELS, POST_STATUS_LABELS } from '../labels'
import type { PostFormErrors, PostFormValues } from '../postForm'
import type { MediaItem, OwnPost, PostCategory } from '../types'
import { MediaPicker } from './MediaPicker'

export type EditorMode = 'create' | 'edit'

type Props = {
  busy: boolean
  confirmingDiscard?: boolean
  draftError?: string
  errors: PostFormErrors
  formError?: string
  verificationRequired?: boolean
  locked: boolean
  mode: EditorMode
  onCancel: () => void
  onChange: (field: keyof PostFormValues, value: string) => void
  onConfirmDiscard?: () => void
  onKeepEditing?: () => void
  onSubmit: (submit: boolean) => void
  onVerify?: () => boolean
  post?: OwnPost
  values: PostFormValues
  verificationTo?: string
  /** Attachments; the picker shows only when a change handler is given. */
  media?: readonly MediaItem[]
  onMediaChange?: (media: MediaItem[]) => void
}

export function PostEditorView({
  busy,
  confirmingDiscard,
  draftError,
  errors,
  formError,
  locked,
  mode,
  onCancel,
  onChange,
  onConfirmDiscard,
  onKeepEditing,
  onSubmit,
  onVerify,
  post,
  values,
  verificationRequired,
  verificationTo = '/verify-email',
  media = [],
  onMediaChange,
}: Props) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>, submit: boolean) => {
    event.preventDefault()
    onSubmit(submit)
  }

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 p-4 pb-16 sm:p-6">
      <PageBanner
        className="px-4 pt-4 pb-20 shadow-sm sm:px-5 [&>svg]:inset-y-auto [&>svg]:bottom-0 [&>svg]:block [&>svg]:h-16 [&>svg]:w-80 [&>svg]:max-w-full lg:pb-4 lg:pr-80 lg:[&>svg]:h-full"
        subtitle="Chia sẻ kinh nghiệm học tập, câu hỏi hoặc văn hoá Trung Hoa cùng cộng đồng."
        title={mode === 'create' ? 'Viết bài cộng đồng' : 'Chỉnh sửa bài viết'}
      />

      {draftError ? (
        <p
          className="rounded-md border border-danger bg-danger-bg p-3.5 text-body-sm text-danger"
          role="alert"
        >
          {draftError}
        </p>
      ) : null}

      {post?.status === 'HIDDEN' ? (
        <Card className="space-y-4 p-5 shadow-sm">
          <div role="status">
            <StatusBadge tone="danger">{POST_STATUS_LABELS.HIDDEN}</StatusBadge>
          </div>
          {post.reviewReason ? (
            <p className="whitespace-pre-wrap rounded-sm border border-line bg-canvas p-3 text-body-sm text-ink">
              {post.reviewReason}
            </p>
          ) : null}
          <div>
            <Button to="/community?tab=mine" variant="outline">
              Về Bài viết của tôi
            </Button>
          </div>
        </Card>
      ) : (
        <>
          {post?.status === 'APPROVED' ? (
            <p
              className="rounded-md border border-warning bg-warning-bg p-3.5 text-body-sm text-warning"
              role="note"
            >
              Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại
            </p>
          ) : null}
          {post?.status === 'REJECTED' && post.reviewReason ? (
            <p
              className="whitespace-pre-wrap rounded-md border border-danger bg-danger-bg p-3.5 text-body-sm text-danger"
              role="note"
            >
              Lý do từ chối: {post.reviewReason}
            </p>
          ) : null}
          {formError ? (
            <p
              className="rounded-md border border-danger bg-danger-bg p-3.5 text-body-sm text-danger"
              role="alert"
            >
              {formError}
            </p>
          ) : null}
          {verificationRequired ? (
            <div>
              <Button
                onClick={(event) => {
                  if (onVerify && !onVerify()) event.preventDefault()
                }}
                to={verificationTo}
                variant="outline"
              >
                Xác minh email
              </Button>
            </div>
          ) : null}

          <Card className="p-5 shadow-sm sm:p-6">
            <form className="space-y-4" onSubmit={(event) => handleSubmit(event, true)}>
              <FormField
                error={errors.title}
                hint={`${values.title.trim().length}/200`}
                htmlFor="post-title"
                label="Tiêu đề"
                required
              >
                <Input
                  disabled={busy || locked}
                  id="post-title"
                  onChange={(event) => onChange('title', event.target.value)}
                  value={values.title}
                />
              </FormField>

              <FormField error={errors.category} htmlFor="post-category" label="Danh mục" required>
                <Select
                  disabled={busy || locked}
                  id="post-category"
                  onChange={(event) => onChange('category', event.target.value)}
                  value={values.category}
                >
                  <option value="">Chọn danh mục</option>
                  {POST_CATEGORIES.map((category: PostCategory) => (
                    <option key={category} value={category}>
                      {POST_CATEGORY_LABELS[category]}
                    </option>
                  ))}
                </Select>
              </FormField>

              <FormField
                error={errors.content}
                hint={`${values.content.trim().length}/50.000`}
                htmlFor="post-content"
                label="Nội dung"
                required
              >
                <Textarea
                  className="min-h-64"
                  disabled={busy || locked}
                  id="post-content"
                  onChange={(event) => onChange('content', event.target.value)}
                  value={values.content}
                />
              </FormField>

              {onMediaChange ? (
                <div className="space-y-2">
                  <p className="text-label text-ink">Ảnh/video</p>
                  <MediaPicker disabled={busy || locked} media={media} onChange={onMediaChange} />
                </div>
              ) : null}

              {locked ? (
                <div>
                  <Button to="/community?tab=mine" variant="outline">
                    Về Bài viết của tôi
                  </Button>
                </div>
              ) : null}

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
                <Button onClick={onCancel} type="button" variant="ghost">
                  Hủy
                </Button>
                <div className="flex flex-wrap gap-3">
                  <Button
                    disabled={busy || locked}
                    onClick={() => onSubmit(false)}
                    type="button"
                    variant="outline"
                  >
                    Lưu nháp
                  </Button>
                  <Button disabled={busy || locked} loading={busy} type="submit">
                    {mode === 'edit' ? 'Gửi duyệt' : 'Gửi duyệt'}
                  </Button>
                </div>
              </div>
            </form>
          </Card>
        </>
      )}

      {post?.status === 'HIDDEN' ? (
        <div>
          <Button onClick={onCancel} type="button" variant="ghost">
            Hủy
          </Button>
        </div>
      ) : null}

      {confirmingDiscard ? (
        <section
          aria-label="Xác nhận bỏ thay đổi"
          className="rounded-md border border-warning bg-warning-bg p-4"
          role="region"
        >
          <p className="text-body-sm font-medium text-ink">
            Bạn có thay đổi chưa lưu. Bỏ các thay đổi và rời trình soạn thảo?
          </p>
          <div className="mt-3 flex flex-wrap gap-3">
            <Button onClick={onConfirmDiscard} type="button" variant="danger">
              Bỏ thay đổi
            </Button>
            <Button onClick={onKeepEditing} type="button" variant="outline">
              Tiếp tục chỉnh sửa
            </Button>
          </div>
        </section>
      ) : null}
    </main>
  )
}
