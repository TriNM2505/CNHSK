import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout, PublicLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { CommunityPostListView } from './components/CommunityPostListView'
import { PostEditorView } from './components/PostEditorView'
import { CommunityPostDetailView } from './components/CommunityPostDetailView'
import { CommentTree } from './components/CommentTree'
import { CommentForm } from './components/CommentForm'
import { ReportForm } from './components/ReportForm'
import { CommentModerationForm } from './components/CommentModerationForm'
import { ownPageWithStatuses, ownPostApproved, ownPostHidden, ownPostRejected, publishedPage, postDetail, publicDiscussion, moderationComment } from './test-fixtures'
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
const moderatorPreviewAuth: AuthContextValue = { ...learnerPreviewAuth, user: { ...previewLearner, roles: ['MODERATOR'] } }

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

const detailPreviews: readonly PreviewEntry[] = [
  { id: 'scr-040-public-ready', title: 'Chi tiết bài — Công khai', render: () => createElement(CommunityPostDetailView, { post: postDetail, likeCount: postDetail.likeCount, canEdit: false }) },
  { id: 'scr-040-public-loading', title: 'Chi tiết bài — Đang tải', render: () => createElement('p', { role: 'status' }, 'Đang tải bài viết…') },
  { id: 'scr-040-public-error', title: 'Chi tiết bài — Lỗi', render: () => createElement('p', { role: 'alert' }, 'Không thể tải bài viết.') },
  { id: 'scr-040-not-found', title: 'Chi tiết bài — Không tìm thấy', render: () => createElement('h1', null, 'Không tìm thấy bài viết') },
  { id: 'scr-040-public-placeholders', title: 'Bình luận — Placeholder và trả lời', render: () => createElement(CommentTree, { postId: 42, comments: publicDiscussion, actions: { learner: false, moderator: false, verified: false, guest: true, disabled: false, now: Date.now(), lockedEdits: new Set<number>(), onSelect: () => undefined }, onRetry: () => undefined }) },
  { id: 'scr-040-comments-empty', title: 'Bình luận — Trống', render: () => createElement('p', null, 'Chưa có bình luận.') },
  { id: 'scr-040-learner-interactions', title: 'Chi tiết bài — LEARNER', render: () => createElement('div', null,
    createElement(CommunityPostDetailView, { post: postDetail, likeCount: postDetail.likeCount, canEdit: false }),
    createElement(CommentTree, { postId: 42, comments: publicDiscussion, actions: { userId: previewLearner.id, learner: true, moderator: false, verified: true, guest: false, disabled: false, now: Date.now(), lockedEdits: new Set<number>(), onSelect: () => undefined }, onRetry: () => undefined }),
    createElement(CommentForm, { value: 'Bình luận mẫu', label: 'Nội dung bình luận', disabled: false, onChange: () => undefined, onSubmit: () => undefined, onCancel: () => undefined })) },
  { id: 'scr-040-moderator-comments', title: 'Bình luận — Kiểm duyệt', render: () => createElement(CommentTree, { postId: 42, comments: [moderationComment], actions: { userId: 101, learner: false, moderator: true, verified: true, guest: false, disabled: false, now: Date.now(), lockedEdits: new Set<number>(), onSelect: () => undefined }, onRetry: () => undefined }) },
  { id: 'scr-040-comment-validation', title: 'Bình luận — Validation', render: () => createElement(CommentForm, { value: '', label: 'Nội dung bình luận', onChange: () => undefined, onSubmit: () => undefined, onCancel: () => undefined, disabled: false, validation: 'Nhập nội dung từ 1 đến 5.000 ký tự.' }) },
  { id: 'scr-040-report-other', title: 'Báo cáo — Trong trang', render: () => createElement(ReportForm, { targetType: 'POST', targetId: 42, disabled: false, onSubmit: () => undefined, onCancel: () => undefined }) },
  { id: 'scr-040-mutation-pending', title: 'Bình luận — Đang gửi', render: () => createElement(CommentForm, { value: 'Nội dung đang gửi', label: 'Nội dung bình luận', disabled: true, onChange: () => undefined, onSubmit: () => undefined, onCancel: () => undefined }) },
  { id: 'scr-040-moderation-confirmation', title: 'Kiểm duyệt — Trong trang', render: () => createElement(CommentModerationForm, { action: 'HIDE', disabled: false, onSubmit: () => undefined, onCancel: () => undefined }) },
].map((entry) => ({ ...entry, group: 'screen' as const, scr: 'SCR-040', fullFrame: true, render: () => createElement(AuthContext.Provider, { value: entry.id.includes('moderator') || entry.id.includes('moderation') ? moderatorPreviewAuth : entry.id === 'scr-040-learner-interactions' ? learnerPreviewAuth : previewAuth }, createElement(PublicLayout, null, entry.render())) }))

export const previews = [...entries, ...detailPreviews] satisfies readonly PreviewEntry[]
