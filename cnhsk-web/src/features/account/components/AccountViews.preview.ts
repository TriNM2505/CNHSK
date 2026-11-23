import type { ChangePasswordViewProps } from './ChangePasswordView'
import type { ProfileViewProps } from './ProfileView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const profileBase = {
  onAvatarFile: () => undefined,
  onLevelChange: () => undefined,
  onRename: async () => true,
  onRetry: () => undefined,
  profile: {
    avatarUrl: null,
    createdAt: '2026-11-02T10:43:00Z',
    deviceCount: 2,
    displayName: 'Minh Anh',
    email: 'minh.anh@example.com',
    emailVerified: true,
    hskLevel: 4,
    id: 27068,
    planCode: 'PREMIUM',
    planExpiresAt: '2027-11-02T10:43:00Z',
  },
  state: 'ready',
} satisfies ProfileViewProps

export const profilePreviews = {
  default: profileBase,
  loading: { ...profileBase, state: 'loading' },
  loadError: {
    ...profileBase,
    loadError: { message: 'Không tải được hồ sơ. Vui lòng thử lại.', requestId: 'mau-req-0007' },
    state: 'error',
  },
  unverified: { ...profileBase, profile: { ...profileBase.profile, emailVerified: false, planCode: 'FREE' } },
  saving: { ...profileBase, busy: 'avatar', uploadProgress: 0.42 },
  saved: {
    ...profileBase,
    notice: { kind: 'success', message: 'Ảnh đại diện mới đã hiện trên toàn bộ trang.', title: 'Đã lưu hồ sơ' },
  },
  validation: {
    ...profileBase,
    notice: { kind: 'error', message: 'Tên hiển thị cần 2–100 ký tự.', title: 'Chưa lưu được' },
  },
  saveError: {
    ...profileBase,
    notice: { kind: 'error', message: 'Chưa lưu được hồ sơ. Vui lòng thử lại.', requestId: 'mau-req-0008', title: 'Chưa lưu được' },
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
