import { ImageUp, X } from 'lucide-react'
import { useId, useRef, useState } from 'react'

import { Button, Input } from '@/shared/ui'

import { MAX_COVER_BYTES, uploadReferenceCover } from '../api/references'

type Props = {
  value: string
  error?: string
  disabled: boolean
  onChange: (url: string) => void
  /** Swappable for tests. */
  upload?: (file: File) => Promise<string>
}

/** Book cover: upload a picture from the computer, or paste a link; the saved value is always a link. */
export function CoverUploadField({
  value,
  error,
  disabled,
  onChange,
  upload = uploadReferenceCover,
}: Props) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [pending, setPending] = useState(false)
  const [problem, setProblem] = useState('')

  const pick = async (file: File | undefined) => {
    if (!file) return
    if (!file.type.startsWith('image/')) return setProblem('Chỉ nhận file ảnh (JPG, PNG, WebP).')
    if (file.size > MAX_COVER_BYTES) return setProblem('Ảnh bìa tối đa 5 MB.')
    setProblem('')
    setPending(true)
    try {
      onChange(await upload(file))
    } catch (cause) {
      setProblem(cause instanceof Error ? cause.message : 'Không tải được ảnh lên.')
    } finally {
      setPending(false)
      if (input.current) input.current.value = ''
    }
  }

  const message = error ?? (problem || undefined)
  return (
    <div className="mb-4 flex min-w-0 flex-col gap-1.5 sm:col-span-2">
      <label className="text-label text-ink" htmlFor={`${id}-url`}>
        Ảnh bìa (không bắt buộc)
      </label>
      <div className="flex min-w-0 flex-wrap items-start gap-3">
        {value ? (
          <img
            alt="Ảnh bìa đã chọn"
            className="aspect-[3/4] w-20 shrink-0 rounded-sm border border-line object-cover"
            src={value}
          />
        ) : null}
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap gap-2">
            <input
              accept="image/*"
              aria-label="Chọn ảnh bìa từ máy"
              className="sr-only"
              disabled={disabled || pending}
              onChange={(event) => void pick(event.target.files?.[0])}
              ref={input}
              tabIndex={-1}
              type="file"
            />
            <Button
              disabled={disabled}
              loading={pending}
              onClick={() => input.current?.click()}
              size="sm"
              type="button"
              variant="outline"
            >
              <ImageUp aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Tải ảnh bìa từ máy
            </Button>
            {value ? (
              <Button
                disabled={disabled || pending}
                onClick={() => onChange('')}
                size="sm"
                type="button"
                variant="ghost"
              >
                <X aria-hidden="true" className="mr-1 inline-block size-4 align-middle" />
                Bỏ ảnh
              </Button>
            ) : null}
          </div>
          <Input
            aria-describedby={`${id}-hint${message ? ` ${id}-error` : ''}`}
            aria-invalid={message ? true : undefined}
            disabled={disabled || pending}
            id={`${id}-url`}
            onChange={(event) => onChange(event.target.value)}
            placeholder="hoặc dán link ảnh https://…"
            value={value}
          />
        </div>
      </div>
      <p className="text-body-sm text-ink-secondary" id={`${id}-hint`}>
        Tải ảnh từ máy (tối đa 5 MB) hoặc dán link ảnh. Để trống thì kệ sách tự vẽ bìa.
      </p>
      {message ? (
        <p className="text-body-sm text-danger" id={`${id}-error`} role="alert">
          {message}
        </p>
      ) : null}
    </div>
  )
}
