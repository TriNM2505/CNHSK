import { cn } from '@/shared/lib/cn'

import hocBaLogo from './assets/hoc-ba-logo.webp'
import hocBaLogoWhite from './assets/hoc-ba-logo-white.svg'

type HocBaLogoProps = {
  className?: string
  /** Empty when the logo sits inside an element that already names it (e.g. a labelled link). */
  alt?: string
  /** White mark for red or dark backgrounds. */
  inverse?: boolean
}

/**
 * Hoc Ba Education logo, used as the product mark with Hoc Ba's permission. It never shrinks, so a crowded header
 * cannot squeeze its width while the height stays fixed.
 */
export function HocBaLogo({ alt = 'Học Bá Education', className, inverse = false }: HocBaLogoProps) {
  return (
    <img
      alt={alt}
      className={cn('h-10 w-auto max-w-none shrink-0 object-contain', className)}
      decoding="async"
      height={inverse ? 53 : 89}
      src={inverse ? hocBaLogoWhite : hocBaLogo}
      width={inverse ? 80 : 134}
    />
  )
}
