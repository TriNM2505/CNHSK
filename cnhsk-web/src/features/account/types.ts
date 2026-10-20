/** Khớp schema Profile của spec 001 OpenAPI. */
export interface Profile {
  displayName: string
  avatarUrl?: string | null
  hskLevel?: number | null
  email: string
  emailVerified: boolean
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
