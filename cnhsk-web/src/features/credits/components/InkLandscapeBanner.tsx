import { useId, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'
import { PageHeader } from '@/shared/ui'

export interface InkLandscapeBannerProps {
  actions?: ReactNode
  className?: string
  subtitle?: ReactNode
  title: ReactNode
}

/**
 * Khung banner thuỷ mặc đỏ Trung Hoa bọc đầu trang (vùng tự do),
 * đồng bộ 100% với SCR-010, SCR-013, SCR-019 (núi thuỷ mặc xếp tầng,
 * mặt trời đỏ, Vạn Lý Trường Thành, đình cổ 2 tầng mái cong, khóm trúc đỏ).
 * Chỉ dùng cho trang trí (aria-hidden="true"), dùng currentColor + semantic token.
 */
export function InkLandscapeBanner({
  actions,
  className,
  subtitle,
  title,
}: InkLandscapeBannerProps) {
  const id = useId()
  const farId = `cnhsk-mtn-far-${id}`
  const midId = `cnhsk-mtn-mid-${id}`
  const frontId = `cnhsk-hill-front-${id}`

  return (
    <div
      className={cn(
        'relative mb-6 overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6',
        className,
      )}
    >
      <PageHeader
        actions={actions}
        className="relative z-nav mb-0"
        subtitle={subtitle}
        title={
          <span className="inline-flex items-center gap-3">
            <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
            <span>{title}</span>
          </span>
        }
      />

      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
        fill="none"
        preserveAspectRatio="xMaxYMid slice"
        viewBox="0 0 680 132"
      >
        <defs>
          <linearGradient id={farId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
          </linearGradient>
          <linearGradient id={midId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
          </linearGradient>
          <linearGradient id={frontId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
          </linearGradient>
        </defs>

        {/* Mặt trời tròn & áng mây ngang cổ phong */}
        <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
        <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
        <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
        <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

        {/* Dãy núi thủy mặc xa (trái sang phải) */}
        <path
          d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
          fill={`url(#${farId})`}
        />
        <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
        <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

        {/* Lớp núi trung cảnh */}
        <path
          d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
          fill={`url(#${midId})`}
        />

        {/* Ngọn đồi tiền cảnh bên phải */}
        <path
          d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
          fill={`url(#${frontId})`}
        />
        <path
          d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
          fill="currentColor"
          opacity="0.22"
        />

        {/* Vạn Lý Trường Thành uốn lượn dọc sườn đồi */}
        <path
          d="M284 132 C328 96, 376 70, 428 72 C474 74, 514 104, 562 102 C598 100, 632 88, 676 94 L676 106 C632 100, 598 112, 562 114 C512 116, 472 86, 428 84 C378 82, 334 106, 298 132 Z"
          fill="currentColor"
          opacity="0.32"
        />
        <path
          d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 102, 562 100 C598 98, 632 86, 676 92"
          opacity="0.58"
          stroke="currentColor"
          strokeDasharray="5 3.5"
          strokeWidth="4.5"
        />
        <path
          d="M294 132 C336 102, 380 78, 428 80 C474 82, 514 112, 562 110 C598 108, 632 96, 676 102"
          opacity="0.45"
          stroke="currentColor"
          strokeWidth="2"
        />

        {/* Đình cổ nhỏ phía xa (trên sườn đồi giữa) */}
        <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
          <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
          <path
            d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z"
            fill="currentColor"
          />
          <path
            d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="11" width="3" x="11" y="24" />
          <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
          <rect fill="currentColor" height="11" width="3" x="30" y="24" />
          <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
        </g>

        {/* Đình cổ nhỏ bên phải (gần khóm trúc) */}
        <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
          <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
          <path
            d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z"
            fill="currentColor"
          />
          <path
            d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="11" width="3" x="11" y="24" />
          <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
          <rect fill="currentColor" height="11" width="3" x="30" y="24" />
          <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
        </g>

        {/* Ngôi đình / chùa cổ 2 tầng mái cong chính (đỏ đậm nổi bật trên đỉnh đồi) */}
        <g className="text-brand-700" opacity="0.95" transform="translate(426, 26)">
          <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
          <path
            d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="6" width="22" x="17" y="17" />
          <path
            d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="15" width="3" x="12" y="29" />
          <rect fill="currentColor" height="15" width="3" x="20" y="29" />
          <rect fill="currentColor" height="15" width="3" x="33" y="29" />
          <rect fill="currentColor" height="15" width="3" x="41" y="29" />
          <rect fill="currentColor" height="2.5" width="34" x="11" y="39" />
          <path d="M7 44 L49 44 L52 49 L4 49 Z" fill="currentColor" />
        </g>

        {/* Khóm tre / trúc đỏ đậm sắc nét ở góc phải */}
        <g className="text-brand-700" opacity="0.92">
          <path d="M634 134 L600 -8" stroke="currentColor" strokeWidth="6" />
          <line stroke="white" strokeWidth="2" x1="622" x2="630" y1="96" y2="94" />
          <line stroke="white" strokeWidth="2" x1="613" x2="621" y1="58" y2="56" />
          <line stroke="white" strokeWidth="2" x1="604" x2="612" y1="20" y2="18" />

          <path d="M654 134 L634 -8" stroke="currentColor" strokeWidth="8.5" />
          <line stroke="white" strokeWidth="2.2" x1="644" x2="654" y1="102" y2="100" />
          <line stroke="white" strokeWidth="2.2" x1="639" x2="649" y1="64" y2="62" />
          <line stroke="white" strokeWidth="2.2" x1="634" x2="644" y1="26" y2="24" />

          <path d="M672 134 L662 -8" stroke="currentColor" strokeWidth="6.5" />
          <line stroke="white" strokeWidth="2" x1="664" x2="673" y1="86" y2="85" />
          <line stroke="white" strokeWidth="2" x1="661" x2="670" y1="46" y2="45" />

          <path
            d="M636 10 Q585 6 542 26"
            stroke="currentColor"
            strokeLinecap="round"
            strokeWidth="2"
          />
          <path
            d="M612 36 Q576 42 548 62"
            stroke="currentColor"
            strokeLinecap="round"
            strokeWidth="1.8"
          />

          <path d="M558 18 Q540 32 528 50 Q548 38 558 18 Z" fill="currentColor" />
          <path d="M570 14 Q558 36 550 58 Q568 40 570 14 Z" fill="currentColor" />
          <path d="M584 12 Q578 34 574 54 Q588 36 584 12 Z" fill="currentColor" />
          <path d="M554 20 Q534 20 518 28 Q540 28 554 20 Z" fill="currentColor" opacity="0.85" />

          <path d="M562 52 Q546 66 536 82 Q554 70 562 52 Z" fill="currentColor" />
          <path d="M576 46 Q566 66 560 86 Q576 68 576 46 Z" fill="currentColor" />
          <path d="M590 42 Q586 60 584 78 Q596 62 590 42 Z" fill="currentColor" />
          <path d="M556 54 Q538 56 524 64 Q544 64 556 54 Z" fill="currentColor" opacity="0.8" />

          <path d="M596 8 Q586 24 582 40 Q596 28 596 8 Z" fill="currentColor" />
          <path d="M618 62 Q606 76 600 92 Q614 80 618 62 Z" fill="currentColor" />
          <path d="M628 58 Q622 76 620 92 Q632 76 628 58 Z" fill="currentColor" />
        </g>
      </svg>
    </div>
  )
}
