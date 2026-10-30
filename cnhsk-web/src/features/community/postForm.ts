import { POST_CATEGORIES } from './labels'
import type { PostCategory, PostWrite } from './types'

export type PostFormValues = { title: string; content: string; category: PostCategory | '' }
export type PostFormErrors = Partial<Record<keyof PostFormValues, string>>

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
