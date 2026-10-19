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
