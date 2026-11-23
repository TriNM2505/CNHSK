import { cn } from '@/shared/lib/cn'

import type { MediaItem } from '../types'

/**
 * Photos and videos of a post, laid out the way a social feed does: one fills the width, two sit side by side,
 * three or four form a grid.
 */
export function MediaGallery({ className, media }: { className?: string; media: readonly MediaItem[] | undefined }) {
  if (!media || media.length === 0) return null
  const single = media.length === 1
  return (
    <div
      className={cn('grid gap-1 overflow-hidden rounded-md', single ? 'grid-cols-1' : 'grid-cols-2', className)}
    >
      {media.map((item, index) => {
        // With three items the first spans the full width so the grid has no hole.
        const wide = media.length === 3 && index === 0
        const frame = cn('w-full bg-ink object-cover', single ? 'max-h-[32rem]' : 'aspect-square', wide && 'col-span-2 aspect-video')
        return item.kind === 'VIDEO' ? (
          <video className={cn(frame, 'object-contain')} controls key={item.publicId} playsInline preload="metadata" src={item.url} />
        ) : (
          <a className={cn(wide && 'col-span-2')} href={item.url} key={item.publicId} rel="noopener noreferrer" target="_blank">
            <img alt="Ảnh đính kèm bài viết" className={frame} loading="lazy" src={item.url} />
          </a>
        )
      })}
    </div>
  )
}
