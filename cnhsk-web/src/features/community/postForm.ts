import { POST_CATEGORIES } from './labels'
import type { PostCategory, PostWrite } from './types'

export type PostFormValues = { title: string; content: string; category: PostCategory | '' }
export type PostFormErrors = Partial<Record<keyof PostFormValues, string>>

export function getPostFormChanges(values: PostFormValues, baseline: PostFormValues): Partial<PostFormValues> {
  const changes: Partial<PostFormValues> = {}
  if (values.title !== baseline.title) changes.title = values.title
  if (values.content !== baseline.content) changes.content = values.content
  if (values.category !== baseline.category) changes.category = values.category
  return changes
}

export function sanitizePostEditorOrigin(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return undefined
  if (value.includes('\\') || [...value].some((character) => character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127)) return undefined
  try {
    const url = new URL(value, 'https://cnhsk.invalid')
    if (url.origin !== 'https://cnhsk.invalid') return undefined
    const postId = /^\/community\/posts\/([0-9]+)$/.exec(url.pathname)?.[1]
    if (url.pathname !== '/community' && parsePostId(postId) === undefined) return undefined
    return `${url.pathname}${url.search}`
  } catch {
    return undefined
  }
}

export function parsePostId(raw: string | undefined): number | undefined {
  if (!raw || !/^\d+$/.test(raw)) return undefined
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
}

export function validatePostForm(values: PostFormValues): PostFormErrors {
  const errors: PostFormErrors = {}
  const title = values.title.trim()
  const content = values.content.trim()
  if (title.length < 1) errors.title = 'Nhập tiêu đề bài viết.'
  else if (title.length > 200) errors.title = 'Tiêu đề không được vượt quá 200 ký tự.'
  if (content.length < 1) errors.content = 'Nhập nội dung bài viết.'
  else if (content.length > 50_000) errors.content = 'Nội dung không được vượt quá 50.000 ký tự.'
  if (!values.category || !POST_CATEGORIES.includes(values.category)) errors.category = 'Chọn danh mục bài viết hợp lệ.'
  return errors
}

export function toPostWrite(values: PostFormValues, submit: boolean): PostWrite | undefined {
  if (
    Object.keys(validatePostForm(values)).length > 0 ||
    !values.category ||
    !POST_CATEGORIES.includes(values.category)
  ) return undefined
  return { title: values.title.trim(), content: values.content.trim(), category: values.category, submit }
}
