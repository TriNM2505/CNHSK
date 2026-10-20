import type { ChangePasswordViewProps } from './ChangePasswordView'
import type { ProfileViewProps } from './ProfileView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const profileBase = {
  email: 'minh.anh@example.com',
  emailVerified: true,
  onChange: () => undefined,
  onRetry: () => undefined,
  onSubmit: () => undefined,
  state: 'ready',
  values: { avatarUrl: '', displayName: 'Minh Anh', hskLevel: '4' },
} satisfies ProfileViewProps

export const profilePreviews = {
  default: profileBase,
  loading: { ...profileBase, state: 'loading' },
  loadError: {
    ...profileBase,
    loadError: {
      message: 'Không tải được hồ sơ. Vui lòng thử lại.',
      requestId: 'mau-req-0007',
    },
    state: 'error',
  },
  unverified: { ...profileBase, emailVerified: false },
  saving: { ...profileBase, state: 'saving' },
  saved: {
    ...profileBase,
    notice: {
      kind: 'success',
      message: 'Tên và ảnh đại diện mới sẽ hiện trên toàn bộ trang.',
      title: 'Đã lưu hồ sơ',
    },
  },
  validation: {
    ...profileBase,
    fieldErrors: {
      avatarUrl: 'Đường dẫn ảnh phải bắt đầu bằng https:// và không có khoảng trắng.',
      displayName: 'Tên hiển thị cần từ 2–100 ký tự.',
    },
    values: { avatarUrl: 'http://anh.example.com/a.png', displayName: 'A', hskLevel: '' },
  },
  saveError: {
    ...profileBase,
    notice: {
      kind: 'error',
      message: 'Chưa lưu được hồ sơ. Vui lòng thử lại.',
      requestId: 'mau-req-0008',
      title: 'Chưa lưu được hồ sơ',
    },
  },
} satisfies Record<string, ProfileViewProps>

const passwordBase = {
  cancelTo: '/me/profile',
  onChange: () => undefined,
  onSubmit: () => undefined,
  state: 'ready',
  values: { confirmPassword: '', currentPassword: '', newPassword: '' },
} satisfies ChangePasswordViewProps

export const changePasswordPreviews = {
  default: passwordBase,
  submitting: {
    ...passwordBase,
    state: 'submitting',
    values: {
      confirmPassword: 'MatKhauMoi1',
      currentPassword: 'MatKhauCu1',
      newPassword: 'MatKhauMoi1',
    },
  },
  validation: {
    ...passwordBase,
    fieldErrors: {
      confirmPassword: 'Mật khẩu nhập lại chưa khớp.',
      currentPassword: 'Vui lòng nhập mật khẩu hiện tại.',
      newPassword: 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.',
    },
    values: { confirmPassword: 'khac', currentPassword: '', newPassword: 'abcdefgh' },
  },
  wrongCurrent: {
    ...passwordBase,
    fieldErrors: { currentPassword: 'Mật khẩu hiện tại không đúng.' },
    values: {
      confirmPassword: 'MatKhauMoi1',
      currentPassword: 'NhapSai1',
      newPassword: 'MatKhauMoi1',
    },
  },
  success: {
    ...passwordBase,
    notice: {
      kind: 'success',
      message: 'Các thiết bị khác đã được đăng xuất. Phiên trên thiết bị này vẫn giữ nguyên.',
      title: 'Đã đổi mật khẩu',
    },
  },
  genericError: {
    ...passwordBase,
    notice: {
      kind: 'error',
      message: 'Chưa đổi được mật khẩu. Vui lòng thử lại.',
      requestId: 'mau-req-0009',
      title: 'Chưa đổi được mật khẩu',
    },
  },
} satisfies Record<string, ChangePasswordViewProps>
