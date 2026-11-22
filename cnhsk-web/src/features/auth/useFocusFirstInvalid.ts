import { useEffect, useRef, type RefObject } from 'react'

/**
 * Sau khi gửi form bị lỗi, đưa con trỏ tới ô lỗi đầu tiên. Chỉ chạy một lần cho mỗi lần gửi
 * nên không giật con trỏ khi người dùng đang sửa ô khác.
 */
export function useFocusFirstInvalid(
  errors: object | string | undefined,
  busy: boolean,
): { form: RefObject<HTMLFormElement>; markSubmitted: () => void } {
  const form = useRef<HTMLFormElement>(null)
  const submitted = useRef(false)

  useEffect(() => {
    if (!submitted.current || busy || !Object.values(errors ?? {}).some(Boolean)) return
    submitted.current = false
    form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [busy, errors])

  return {
    form,
    markSubmitted: () => {
      submitted.current = true
    },
  }
}
