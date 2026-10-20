export type RoleCode =
  'LEARNER' | 'TEACHER' | 'MODERATOR' | 'CONTENT_EDITOR' | 'ACCOUNTANT' | 'ADMIN'

export interface CurrentUser {
  id: number
  email: string
  displayName: string
  emailVerified: boolean
  roles: RoleCode[]
  hskLevel?: number | null
  avatarUrl?: string | null
}

export interface EmailRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export interface TokenRequest {
  token: string
}

export interface EmailVerifiedResponse {
  emailVerified: true
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  user: CurrentUser
  accessToken?: string
  refreshToken?: string
  accessTokenExpiresIn?: number
}

export interface RegisterRequest {
  email: string
  password: string
  confirmPassword: string
  displayName: string
  acceptTerms: boolean
}

export interface RegisterResponse {
  userId: number
  email: string
  emailVerified: boolean
}
