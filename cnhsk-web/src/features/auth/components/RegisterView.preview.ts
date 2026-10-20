import type { RegisterViewProps } from './RegisterView'

const idleCallbacks = {
  loginTo: '/login?redirect=%2Flearn%2Ftopics',
  onAcceptTermsChange: () => undefined,
  onConfirmPasswordChange: () => undefined,
  onDisplayNameChange: () => undefined,
  onEmailChange: () => undefined,
  onPasswordChange: () => undefined,
  onSubmit: () => undefined,
} satisfies Pick<
  RegisterViewProps,
  | 'loginTo'
  | 'onAcceptTermsChange'
  | 'onConfirmPasswordChange'
  | 'onDisplayNameChange'
  | 'onEmailChange'
  | 'onPasswordChange'
  | 'onSubmit'
>

const completedValues = {
  acceptTerms: true,
  confirmPassword: 'HocTieng1',
  displayName: 'Nguyễn An',
  email: 'nguyen.an@example.com',
  password: 'HocTieng1',
}

export const registerPreviews = {
  default: {
    ...idleCallbacks,
    state: 'ready',
    values: {
      acceptTerms: false,
      confirmPassword: '',
      displayName: '',
      email: '',
      password: '',
    },
  },
  submitting: {
    ...idleCallbacks,
    state: 'submitting',
    values: completedValues,
  },
  clientValidation: {
    ...idleCallbacks,
    fieldErrors: {
      acceptTerms: 'Bạn cần đồng ý với điều khoản để đăng ký.',
      confirmPassword: 'Mật khẩu nhập lại chưa khớp.',
      displayName: 'Tên hiển thị cần từ 2–100 ký tự.',
      email: 'Vui lòng nhập email đúng định dạng.',
      password: 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.',
    },
    notice: { kind: 'validation' },
    state: 'error',
    values: {
      acceptTerms: false,
      confirmPassword: 'KhacMatKhau1',
      displayName: 'A',
      email: 'email-khong-hop-le',
      password: 'matkhau',
    },
  },
  emailExists: {
    ...idleCallbacks,
    fieldErrors: { email: 'Email này đã được sử dụng.' },
    notice: { kind: 'email-exists' },
    state: 'error',
    values: completedValues,
  },
  fieldErrors: {
    ...idleCallbacks,
    fieldErrors: {
      displayName: 'Tên hiển thị chứa ký tự không hợp lệ.',
      email: 'Email chưa hợp lệ.',
    },
    notice: { kind: 'validation' },
    state: 'error',
    values: completedValues,
  },
  genericError: {
    ...idleCallbacks,
    notice: {
      kind: 'generic',
      message: 'Không thể đăng ký lúc này. Vui lòng thử lại.',
      requestId: 'req-register-123',
    },
    state: 'error',
    values: completedValues,
  },
} as const satisfies Record<string, RegisterViewProps>
