/** Khớp schema Profile của spec 001 OpenAPI. */
export interface Profile {
  displayName: string
  avatarUrl?: string | null
  hskLevel?: number | null
  email: string
  emailVerified: boolean
  /** Read-only account facts shown on the profile page. */
  id?: number
  createdAt?: string
  planCode?: string
  planExpiresAt?: string | null
  deviceCount?: number
}

/** Khớp schema ProfilePatch — chỉ 3 trường được sửa (FR-051, FR-052). */
export interface ProfilePatch {
  displayName?: string
  avatarUrl?: string | null
  hskLevel?: number | null
}

/** Khớp schema ChangePasswordRequest. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}
