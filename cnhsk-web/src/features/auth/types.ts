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
