import { ImagePlus, Loader2, Play, X } from 'lucide-react'
import { useRef, useState, type ChangeEvent } from 'react'

import { ApiError } from '@/shared/api'

import { getUploadSignature, MAX_MEDIA_ITEMS, rejectFile, uploadToCloudinary } from '../api/media'
import type { MediaItem } from '../types'

type Uploading = { key: string; name: string; progress: number }

/**
 * Attach up to four photos or videos. Files go straight to Cloudinary; only their addresses reach the post. When the
 * server has no Cloudinary account configured the picker explains that instead of failing on submit.
 */
export function MediaPicker({
  disabled,
  media,
  onChange,
}: {
  disabled?: boolean
  media: readonly MediaItem[]
  onChange: (media: MediaItem[]) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState<Uploading[]>([])
  const [error, setError] = useState<string>()
  const [unavailable, setUnavailable] = useState(false)
  const room = MAX_MEDIA_ITEMS - media.length - uploading.length

  async function addFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).slice(0, Math.max(room, 0))
    event.target.value = ''
    if (files.length === 0) return
    const problems = files.map(rejectFile).filter(Boolean)
    if (problems.length > 0) {
      setError(problems.join(' '))
      return
    }
    setError(undefined)
    let signature
    try {
      signature = await getUploadSignature()
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 503) setUnavailable(true)
      else setError('Chưa chuẩn bị được việc tải lên, thử lại.')
      return
    }
    const added: MediaItem[] = []
    await Promise.all(
      files.map(async (file, index) => {
        const key = `${Date.now()}-${index}`
        setUploading((current) => [...current, { key, name: file.name, progress: 0 }])
        try {
          const item = await uploadToCloudinary(file, signature, (progress) =>
            setUploading((current) => current.map((entry) => (entry.key === key ? { ...entry, progress } : entry))),
          )
          added.push(item)
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : `Không tải được ${file.name} lên.`)
        } finally {
          setUploading((current) => current.filter((entry) => entry.key !== key))
        }
      }),
    )
    if (added.length > 0) onChange([...media, ...added].slice(0, MAX_MEDIA_ITEMS))
  }

  if (unavailable) {
    return (
      <p className="rounded-md bg-canvas p-3 text-body-sm text-ink-secondary">
        Máy chủ chưa bật tính năng đính kèm ảnh, video.
      </p>
    )
  }

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-4 gap-2">
        {media.map((item, index) => (
          <div className="relative aspect-square overflow-hidden rounded-md bg-ink" key={item.publicId}>
            {item.kind === 'VIDEO' ? (
              <>
                <video className="size-full object-cover" muted preload="metadata" src={item.url} />
                <Play aria-hidden="true" className="absolute inset-0 m-auto size-8 fill-surface text-surface" />
              </>
            ) : (
              <img alt="" className="size-full object-cover" src={item.url} />
            )}
            <button
              aria-label={`Bỏ tệp thứ ${index + 1}`}
              className="absolute right-1 top-1 flex size-7 items-center justify-center rounded-full bg-ink/70 text-surface hover:bg-ink"
              disabled={disabled}
              onClick={() => onChange(media.filter((_, position) => position !== index))}
              type="button"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          </div>
        ))}
        {uploading.map((entry) => (
          <div
            className="flex aspect-square flex-col items-center justify-center gap-1 rounded-md border border-dashed border-line bg-canvas p-2 text-center"
            key={entry.key}
            role="status"
          >
            <Loader2 aria-hidden="true" className="size-5 animate-spin text-brand-600" />
            <span className="text-caption tabular-nums text-ink-secondary">{Math.round(entry.progress * 100)}%</span>
            <span className="sr-only">Đang tải {entry.name}</span>
          </div>
        ))}
        {room > 0 ? (
          <button
            className="flex aspect-square flex-col items-center justify-center gap-1 rounded-md border border-dashed border-brand-100 bg-surface text-brand-700 transition-colors hover:border-brand-600 hover:bg-brand-50 disabled:opacity-60"
            disabled={disabled}
            onClick={() => input.current?.click()}
            type="button"
          >
            <ImagePlus aria-hidden="true" className="size-6" />
            <span className="text-caption">Ảnh/video</span>
          </button>
        ) : null}
      </div>
      <input
        accept="image/*,video/*"
        className="hidden"
        multiple
        onChange={(event) => void addFiles(event)}
        ref={input}
        type="file"
      />
      <p className="text-caption text-ink-muted">Tối đa 4 tệp · ảnh ≤ 10 MB · video ≤ 100 MB</p>
      {error ? (
        <p className="text-caption text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
