import { useEffect, useRef, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export type PandaMood = 'welcome' | 'mail' | 'question' | 'thumbs-up' | 'sad'

/** Khung nền tre tối giản dùng chung cho 6 màn auth; trừ 4rem chiều cao header PublicLayout. */
export function AuthBambooShell({ children }: { children: ReactNode }) {
  return (
    <div className="relative isolate flex min-h-[calc(100vh-4rem)] flex-col overflow-hidden">
      <LoginBambooBackdrop />
      {children}
    </div>
  )
}

function GeometricCulm({ x, width, nodes }: { x: number; width: number; nodes: number[] }) {
  return (
    <g>
      <rect
        className="fill-ink-muted opacity-20"
        height="600"
        rx={width / 2}
        width={width}
        x={x}
        y="20"
      />
      {nodes.map((ny) => (
        <rect
          className="fill-canvas opacity-80"
          height="3"
          key={ny}
          rx="1.5"
          width={width + 4}
          x={x - 2}
          y={ny}
        />
      ))}
    </g>
  )
}

function GeometricLeafPair({ x, y, flip = false }: { x: number; y: number; flip?: boolean }) {
  return (
    <g transform={`translate(${x}, ${y}) scale(${flip ? -1 : 1}, 1)`}>
      <path className="fill-ink-muted opacity-25" d="M 0 0 Q 22 -14 44 -4 Q 22 6 0 0 Z" />
      <path className="fill-brand-600 opacity-15" d="M 0 2 Q 18 14 36 22 Q 16 18 0 2 Z" />
    </g>
  )
}

const FALLING_LEAVES = [
  { left: '4%', top: '12%', duration: 12000, delay: 900, sway: 22 },
  { left: '9%', top: '64%', duration: 13800, delay: 5400, sway: -20 },
  { left: '72%', top: '18%', duration: 12600, delay: 2200, sway: 24 },
  { left: '84%', top: '52%', duration: 14400, delay: 7100, sway: -22 },
  { left: '92%', top: '28%', duration: 11800, delay: 3900, sway: 18 },
  { left: '95%', top: '76%', duration: 13200, delay: 8300, sway: -18 },
] as const

/** Lớp nền cây tre hình học tối giản và lá rơi nhẹ (tự tắt khi prefers-reduced-motion). */
export function LoginBambooBackdrop() {
  const leavesRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const root = leavesRef.current
    if (!root) return
    const reduced =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return

    const animations: Animation[] = []
    root.querySelectorAll<HTMLElement>('[data-bamboo-leaf]').forEach((el, idx) => {
      const cfg = FALLING_LEAVES[idx]
      if (!cfg || typeof el.animate !== 'function') return
      animations.push(
        el.animate(
          [
            { top: '-6%', transform: 'translateX(0px) rotate(0deg)', opacity: 0 },
            { offset: 0.15, opacity: 0.55 },
            { offset: 0.5, transform: `translateX(${cfg.sway}px) rotate(120deg)`, opacity: 0.6 },
            { offset: 0.85, opacity: 0.45 },
            {
              top: '104%',
              transform: `translateX(${-cfg.sway * 0.5}px) rotate(240deg)`,
              opacity: 0,
            },
          ],
          {
            delay: -cfg.delay,
            duration: cfg.duration,
            easing: 'ease-in-out',
            iterations: Infinity,
          },
        ),
      )
    })
    return () => animations.forEach((anim) => anim.cancel())
  }, [])

  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 left-0 h-full w-40 sm:w-56"
        focusable="false"
        preserveAspectRatio="xMinYMax slice"
        viewBox="0 0 200 640"
      >
        <GeometricCulm nodes={[110, 220, 330, 440, 540]} width={10} x={24} />
        <GeometricCulm nodes={[140, 260, 380, 500]} width={7} x={52} />
        <GeometricLeafPair x={34} y={112} />
        <GeometricLeafPair flip x={52} y={262} />
        <GeometricLeafPair x={34} y={332} />
      </svg>

      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 right-0 h-full w-40 sm:w-56"
        focusable="false"
        preserveAspectRatio="xMaxYMax slice"
        viewBox="0 0 200 640"
      >
        <GeometricCulm nodes={[120, 230, 340, 450, 550]} width={10} x={162} />
        <GeometricCulm nodes={[150, 270, 390, 510]} width={7} x={138} />
        <GeometricLeafPair flip x={162} y={122} />
        <GeometricLeafPair x={145} y={272} />
        <GeometricLeafPair flip x={162} y={342} />
      </svg>

      <div className="pointer-events-none absolute inset-0" ref={leavesRef}>
        {FALLING_LEAVES.map((leaf) => (
          <span
            className="pointer-events-none absolute"
            data-bamboo-leaf=""
            key={leaf.left}
            style={{ left: leaf.left, top: leaf.top }}
          >
            <svg
              aria-hidden="true"
              className="pointer-events-none w-6"
              focusable="false"
              viewBox="0 0 36 14"
            >
              <path className="fill-ink-muted opacity-40" d="M 0 7 Q 18 -2 36 7 Q 18 16 0 7 Z" />
            </svg>
          </span>
        ))}
      </div>
    </div>
  )
}

function ScholarPandaFace({ mood }: { mood: PandaMood }) {
  if (mood === 'sad') {
    return (
      <>
        <path
          className="fill-none stroke-ink"
          d="M 86 78 L 100 72 M 154 78 L 140 72"
          strokeLinecap="round"
          strokeWidth="3.2"
        />
        <ellipse
          className="fill-ink"
          cx="95"
          cy="92"
          rx="13"
          ry="15"
          transform="rotate(-10 95 92)"
        />
        <ellipse
          className="fill-ink"
          cx="145"
          cy="92"
          rx="13"
          ry="15"
          transform="rotate(10 145 92)"
        />
        <circle className="fill-surface" cx="92" cy="88" r="3.6" />
        <circle className="fill-surface" cx="142" cy="88" r="3.6" />
        <path
          className="fill-info-bg stroke-info"
          d="M 89 102 Q 86 116 91 122 Q 97 116 95 102 Z"
          strokeWidth="2"
        />
        <path
          className="fill-info-bg stroke-info"
          d="M 145 102 Q 143 116 149 122 Q 154 116 151 102 Z"
          strokeWidth="2"
        />
        <ellipse className="fill-ink" cx="120" cy="102" rx="6" ry="4" />
        <path
          className="fill-none stroke-ink"
          d="M 113 116 Q 120 110 127 116"
          strokeLinecap="round"
          strokeWidth="3"
        />
      </>
    )
  }

  return (
    <>
      <ellipse className="fill-ink" cx="95" cy="91" rx="13" ry="15" transform="rotate(-10 95 91)" />
      <ellipse
        className="fill-ink"
        cx="145"
        cy="91"
        rx="13"
        ry="15"
        transform="rotate(10 145 91)"
      />
      {mood === 'mail' || mood === 'thumbs-up' ? (
        <>
          <path
            className="fill-none stroke-surface"
            d="M 88 92 Q 95 84 102 92"
            strokeLinecap="round"
            strokeWidth="3.2"
          />
          <path
            className="fill-none stroke-surface"
            d="M 138 92 Q 145 84 152 92"
            strokeLinecap="round"
            strokeWidth="3.2"
          />
        </>
      ) : (
        <>
          <circle className="fill-surface" cx="93" cy="88" r="4" />
          <circle className="fill-surface" cx="143" cy="88" r="4" />
        </>
      )}
      {mood === 'question' ? (
        <path
          className="fill-none stroke-ink"
          d="M 85 73 Q 95 68 104 74 M 138 76 Q 146 74 154 78"
          strokeLinecap="round"
          strokeWidth="3"
        />
      ) : null}
      <ellipse className="fill-brand-100" cx="79" cy="106" rx="7" ry="4" />
      <ellipse className="fill-brand-100" cx="161" cy="106" rx="7" ry="4" />
      <ellipse className="fill-ink" cx="120" cy="101" rx="6" ry="4" />
      {mood === 'question' ? (
        <circle className="fill-none stroke-ink" cx="120" cy="113" r="4" strokeWidth="2.8" />
      ) : (
        <path
          className="fill-none stroke-ink"
          d="M 111 110 Q 120 119 129 110"
          strokeLinecap="round"
          strokeWidth="3"
        />
      )}
    </>
  )
}

/** Nhân vật gấu trúc học đường CNHSK dựng bằng hình khối phẳng, dùng chung cho 6 màn auth. */
function ScholarPandaSvg({
  className,
  mood = 'welcome',
}: {
  className?: string
  mood?: PandaMood
}) {
  const headTilt = mood === 'question' ? 'rotate(-5 120 92)' : undefined

  return (
    <svg
      aria-hidden="true"
      className={cn('pointer-events-none', className)}
      focusable="false"
      viewBox="0 0 240 230"
    >
      {/* Thân bo tròn cân đối */}
      <rect
        className="fill-surface stroke-ink"
        height="82"
        rx="36"
        strokeWidth="3.5"
        width="90"
        x="75"
        y="128"
      />
      <rect className="fill-canvas" height="58" rx="26" width="58" x="91" y="142" />

      {/* Tay theo từng tư thế */}
      {mood === 'welcome' ? (
        <>
          <rect
            className="fill-ink"
            height="44"
            rx="12"
            transform="rotate(-38 66 146)"
            width="22"
            x="55"
            y="124"
          />
          <rect
            className="fill-ink"
            height="44"
            rx="12"
            transform="rotate(18 172 158)"
            width="22"
            x="161"
            y="136"
          />
          <g transform="translate(154, 148) rotate(8)">
            <rect
              className="fill-brand-50 stroke-brand-600"
              height="38"
              rx="6"
              strokeWidth="2.6"
              width="32"
              x="0"
              y="0"
            />
            <text className="fill-brand-700 text-body font-bold" x="8" y="25">
              汉
            </text>
          </g>
        </>
      ) : mood === 'mail' ? (
        <>
          <rect
            className="fill-ink"
            height="40"
            rx="11"
            transform="rotate(24 72 156)"
            width="20"
            x="62"
            y="136"
          />
          <rect
            className="fill-ink"
            height="40"
            rx="11"
            transform="rotate(-24 168 156)"
            width="20"
            x="158"
            y="136"
          />
          <g transform="translate(86, 150)">
            <rect
              className="fill-surface stroke-ink"
              height="42"
              rx="6"
              strokeWidth="3"
              width="68"
              x="0"
              y="0"
            />
            <path
              className="fill-none stroke-ink"
              d="M 3 4 L 34 25 L 65 4"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2.8"
            />
            <circle className="fill-brand-600" cx="34" cy="24" r="5" />
          </g>
        </>
      ) : mood === 'question' ? (
        <>
          <rect
            className="fill-ink"
            height="44"
            rx="11"
            transform="rotate(35 66 138)"
            width="20"
            x="56"
            y="116"
          />
          <rect
            className="fill-ink"
            height="42"
            rx="11"
            transform="rotate(-16 170 158)"
            width="20"
            x="160"
            y="138"
          />
          <text className="fill-brand-700 text-display-sm font-bold" x="28" y="64">
            ?
          </text>
        </>
      ) : mood === 'thumbs-up' ? (
        <>
          <rect
            className="fill-ink"
            height="42"
            rx="11"
            transform="rotate(16 70 158)"
            width="20"
            x="60"
            y="138"
          />
          <rect
            className="fill-ink"
            height="42"
            rx="11"
            transform="rotate(-48 176 148)"
            width="20"
            x="166"
            y="128"
          />
          <circle className="fill-ink" cx="192" cy="134" r="11" />
          <rect className="fill-ink" height="20" rx="5.5" width="11" x="187" y="109" />
          <circle className="fill-accent-500" cx="206" cy="104" r="4.5" />
        </>
      ) : (
        <>
          <rect
            className="fill-ink"
            height="38"
            rx="11"
            transform="rotate(28 76 160)"
            width="20"
            x="66"
            y="140"
          />
          <rect
            className="fill-ink"
            height="38"
            rx="11"
            transform="rotate(-28 164 160)"
            width="20"
            x="154"
            y="140"
          />
        </>
      )}

      {/* Khăn quàng đỏ thương hiệu CNHSK */}
      <rect
        className="fill-brand-600 stroke-brand-800"
        height="16"
        rx="8"
        strokeWidth="2.5"
        width="84"
        x="78"
        y="124"
      />
      <path
        className="fill-brand-700 stroke-brand-800"
        d="M 134 136 L 152 162 L 136 164 L 124 138 Z"
        strokeLinejoin="round"
        strokeWidth="2.2"
      />

      {/* Hai bàn chân ngồi gọn */}
      <ellipse className="fill-ink" cx="92" cy="206" rx="18" ry="12" />
      <ellipse className="fill-ink" cx="148" cy="206" rx="18" ry="12" />

      {/* Đầu squircle bo tròn + tai */}
      <g transform={headTilt}>
        <circle className="fill-ink" cx="76" cy="54" r="18" />
        <circle className="fill-ink" cx="164" cy="54" r="18" />
        <rect
          className="fill-surface stroke-ink"
          height="88"
          rx="40"
          strokeWidth="3.5"
          width="114"
          x="63"
          y="44"
        />
        <ScholarPandaFace mood={mood} />
      </g>
    </svg>
  )
}

/** Gấu trúc nhỏ đồng bộ khuôn mặt CNHSK bám góc card ở màn hình >= sm. */
export function CardCornerPandas() {
  const miniHead = (
    <svg
      aria-hidden="true"
      className="pointer-events-none w-16"
      focusable="false"
      viewBox="0 0 100 84"
    >
      <circle className="fill-ink" cx="22" cy="22" r="12" />
      <circle className="fill-ink" cx="78" cy="22" r="12" />
      <rect
        className="fill-surface stroke-ink"
        height="58"
        rx="26"
        strokeWidth="3"
        width="76"
        x="12"
        y="16"
      />
      <ellipse className="fill-ink" cx="35" cy="44" rx="8" ry="10" transform="rotate(-10 35 44)" />
      <ellipse className="fill-ink" cx="65" cy="44" rx="8" ry="10" transform="rotate(10 65 44)" />
      <circle className="fill-surface" cx="34" cy="42" r="2.6" />
      <circle className="fill-surface" cx="64" cy="42" r="2.6" />
      <ellipse className="fill-ink" cx="50" cy="50" rx="4" ry="2.8" />
      <path
        className="fill-none stroke-ink"
        d="M 44 56 Q 50 61 56 56"
        strokeLinecap="round"
        strokeWidth="2.5"
      />
      <rect className="fill-brand-600" height="8" rx="4" width="44" x="28" y="68" />
    </svg>
  )

  return (
    <div aria-hidden="true" className="pointer-events-none hidden sm:block">
      <div className="absolute -left-6 -top-9 z-20 -rotate-12">{miniHead}</div>
      <div className="absolute -right-6 -top-9 z-20 rotate-12">{miniHead}</div>
    </div>
  )
}

/** Cột minh họa bên phải cho khuôn K3 (SCR-002, 003, 005, 006). */
export function RightGiantPandaScene({ mood = 'welcome' }: { mood?: PandaMood }) {
  return (
    <div className="relative flex w-full items-end justify-center pt-12 lg:mt-10 lg:translate-y-4">
      <ScholarPandaSvg className="relative z-10 w-72 xl:w-80" mood={mood} />
    </div>
  )
}

/** Minh họa gấu trúc CNHSK cho khuôn K2 (SCR-004 cầm thư, SCR-079 ngồi buồn). */
export function PandaIllustration({ className, mood }: { className?: string; mood: PandaMood }) {
  return <ScholarPandaSvg className={className} mood={mood} />
}
