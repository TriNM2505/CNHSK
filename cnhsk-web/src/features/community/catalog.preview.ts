import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout, PublicLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { CommunityPostListView } from './components/CommunityPostListView'
import { PostEditorView } from './components/PostEditorView'
import { ownPageWithStatuses, ownPostApproved, ownPostHidden, ownPostRejected, publishedPage } from './test-fixtures'
import type { PostFormValues } from './postForm'
import type { OwnPost } from './types'

const previewAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'guest',
  user: null,
}
const learnerPreviewAuth: AuthContextValue = { ...previewAuth, status: 'authenticated', user: previewLearner }

const baseListProps = {
  category: '' as const,
  categories: ['EXPERIENCE', 'QUESTION', 'STUDY_BUDDY', 'CULTURE', 'JOB'] as const,
  isLearner: false,
  onCategoryChange: () => undefined,
  onPageChange: () => undefined,
  onRetry: () => undefined,
  onStatusChange: () => undefined,
  onTabChange: () => undefined,
  onWrite: () => undefined,
  page: 0,
  size: 20,
  status: '' as const,
}

function listPreview(mode: 'public' | 'mine', state: 'loading' | 'empty' | 'error' | 'ready') {
  const isMine = mode === 'mine'
  const data = state === 'ready' ? (isMine ? ownPageWithStatuses : publishedPage) : undefined
  return createElement(
    AuthContext.Provider,
    { value: previewAuth },
    createElement(
      PublicLayout,
      null,
      createElement(CommunityPostListView, {
        ...baseListProps,
        data,
        error: state === 'error' ? 'Không thể tải bài viết.' : undefined,
        isLearner: isMine,
        mode,
        state,
      }),
    ),
  )
}

const editorValues: PostFormValues = { title: 'Chia sẻ cách học từ mới', content: 'Nội dung bài viết mẫu.', category: 'EXPERIENCE' }
function editorPreview(post?: OwnPost, overrides: Partial<Parameters<typeof PostEditorView>[0]> = {}) {
  return createElement(
    AuthContext.Provider,
    { value: learnerPreviewAuth },
    createElement(
      LearnerLayout,
      null,
      createElement(PostEditorView, { busy: false, errors: {}, locked: false, mode: post ? 'edit' : 'create', onChange: () => undefined, onSubmit: () => undefined, post, values: editorValues, ...overrides }),
    ),
  )
}

const entries: readonly PreviewEntry[] = [
  { fullFrame: true, group: 'screen', id: 'scr-039-public-ready', render: () => listPreview('public', 'ready'), scr: 'SCR-039', title: 'Bài công khai — Có dữ liệu' },
  { fullFrame: true, group: 'screen', id: 'scr-039-public-empty', render: () => listPreview('public', 'empty'), scr: 'SCR-039', title: 'Bài công khai — Trống' },
  { fullFrame: true, group: 'screen', id: 'scr-039-public-loading', render: () => listPreview('public', 'loading'), scr: 'SCR-039', title: 'Bài công khai — Đang tải' },
  { fullFrame: true, group: 'screen', id: 'scr-039-public-error', render: () => listPreview('public', 'error'), scr: 'SCR-039', title: 'Bài công khai — Lỗi' },
  { fullFrame: true, group: 'screen', id: 'scr-039-mine-statuses', render: () => listPreview('mine', 'ready'), scr: 'SCR-039', title: 'Bài viết của tôi — Các trạng thái' },
  { fullFrame: true, group: 'screen', id: 'scr-041-create', render: () => editorPreview(), scr: 'SCR-041', title: 'Tạo bài viết' },
  { fullFrame: true, group: 'screen', id: 'scr-041-edit-approved', render: () => editorPreview(ownPostApproved), scr: 'SCR-041', title: 'Sửa bài đã đăng — Cảnh báo duyệt lại' },
  { fullFrame: true, group: 'screen', id: 'scr-041-edit-rejected', render: () => editorPreview(ownPostRejected), scr: 'SCR-041', title: 'Sửa bài bị từ chối — Lý do' },
  { fullFrame: true, group: 'screen', id: 'scr-041-edit-hidden', render: () => editorPreview(ownPostHidden), scr: 'SCR-041', title: 'Bài bị ẩn — Chỉ đọc' },
  { fullFrame: true, group: 'screen', id: 'scr-041-validation-error', render: () => editorPreview(undefined, { errors: { title: 'Nhập tiêu đề bài viết.' }, formError: 'Vui lòng kiểm tra các trường.' }), scr: 'SCR-041', title: 'Tạo bài — Lỗi kiểm tra' },
  { fullFrame: true, group: 'screen', id: 'scr-041-submitting', render: () => editorPreview(undefined, { busy: true }), scr: 'SCR-041', title: 'Tạo bài — Đang gửi' },
]

export const previews = entries satisfies readonly PreviewEntry[]
