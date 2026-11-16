import { useEffect, useRef } from 'react'

import { cn } from '@/shared/lib/cn'

/**
 * BẢN THỬ (chưa duyệt) — SCR-002:
 * 1. Cây trúc có thân đốt đầy đặn, uốn cong hình cánh cung tự nhiên và tán lá trúc RỦ XUỐNG mềm mại.
 * 2. Xóa cây trúc phía sau chú gấu trúc lớn bên phải, đồng thời lùi chú gấu lớn xuống phía dưới.
 * 3. 4 chú gấu trúc Chibi nhỏ bám ở 4 góc khung form.
 * 4. Lá trúc rơi tự nhiên ở lớp nền, không đè vào khung form.
 */

/**
 * Chùm lá trúc RỦ XUỐNG mềm mại (theo đúng dáng lá trúc rủ ở ô 2 & ô 3 ảnh mẫu):
 * Các phiến lá uốn cong từ cuống và buông rủ mũi nhọn xuống dưới theo trọng lực.
 */
function WeepingLeafBunch({
  className,
  scale = 1,
  transform = '',
}: {
  className?: string
  scale?: number
  transform?: string
}) {
  return (
    <g className={className} transform={`${transform} scale(${scale})`}>
      {/* Nhành cuống mảnh uốn cong rủ xuống */}
      <path
        className="fill-none stroke-success"
        d="M 0 0 Q 16 -4 34 10 Q 48 22 56 38"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
      <path
        className="fill-none stroke-success opacity-80"
        d="M 14 0 Q 6 14 -2 30"
        strokeLinecap="round"
        strokeWidth="1.4"
      />

      {/* Tầng lá rủ bên trái cuống */}
      <path
        className="fill-success opacity-90"
        d="M 8 -1 C -4 10, -16 28, -22 54 C -8 34, 2 16, 8 -1 Z"
      />
      <path className="fill-success" d="M 14 1 C 6 16, 0 38, -2 66 C 8 42, 12 20, 14 1 Z" />
      <path
        className="fill-success opacity-75"
        d="M 4 -1 C -14 4, -30 16, -42 36 C -24 22, -8 10, 4 -1 Z"
      />

      {/* Tầng lá rủ chính giữa (dài, cong mềm buông xuống) */}
      <path className="fill-success" d="M 26 6 C 20 24, 18 48, 20 76 C 28 50, 28 26, 26 6 Z" />
      <path
        className="fill-success opacity-85"
        d="M 32 10 C 32 28, 36 52, 44 78 C 44 50, 38 26, 32 10 Z"
      />
      <path className="fill-success" d="M 22 4 C 34 16, 48 34, 58 60 C 44 40, 32 20, 22 4 Z" />

      {/* Tầng lá rủ ở đầu ngọn cành */}
      <path
        className="fill-success opacity-95"
        d="M 46 24 C 42 44, 42 66, 46 90 C 52 66, 50 42, 46 24 Z"
      />
      <path
        className="fill-success opacity-80"
        d="M 50 28 C 56 44, 66 64, 78 84 C 66 62, 58 42, 50 28 Z"
      />
      <path
        className="fill-success opacity-70"
        d="M 48 26 C 62 32, 78 44, 92 62 C 76 48, 60 36, 48 26 Z"
      />
    </g>
  )
}

/**
 * Thân trúc chia đốt đầy đặn, gốc to vững, càng lên cao càng uốn cong mềm mại
 * như cần trúc gặp gió, kèm các cành phụ và chùm lá trúc rủ xuống.
 */
function WeepingBambooStalk({
  baseW = 11,
  bend = 110,
  op = 'opacity-90',
  startX = 48,
}: {
  baseW?: number
  bend?: number
  op?: string
  startX?: number
}) {
  // Đường cong Bezier bậc 3: gốc thẳng đứng rồi uốn cong mềm sang ngang ở nửa trên
  const x0 = startX
  const y0 = 700
  const x1 = startX + bend * 0.12
  const y1 = 440
  const x2 = startX + bend * 0.52
  const y2 = 200
  const x3 = startX + bend
  const y3 = 28

  const tSteps = [0, 0.13, 0.26, 0.39, 0.52, 0.65, 0.78, 0.9, 1.0]
  const pts = tSteps.map((t) => {
    const u = 1 - t
    const x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
    const y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
    const w = baseW * (1 - t * 0.58)
    return { t, w, x, y }
  })

  return (
    <g className={op}>
      {/* Vẽ từng lóng trúc nối tiếp nhau với độ dày thon dần từ gốc lên ngọn */}
      {pts.slice(0, -1).map((p, idx) => {
        const next = pts[idx + 1]!
        const midX = (p.x + next.x) / 2 + bend * 0.015
        const midY = (p.y + next.y) / 2
        return (
          <g key={p.t}>
            {/* Thân lóng trúc */}
            <path
              className="fill-none stroke-success"
              d={`M ${p.x} ${p.y} Q ${midX} ${midY} ${next.x} ${next.y}`}
              strokeLinecap="butt"
              strokeWidth={p.w}
            />
            {/* Sống sáng dọc ống trúc tạo khối tròn */}
            <path
              className="fill-none stroke-success-bg opacity-55"
              d={`M ${p.x - p.w * 0.18} ${p.y} Q ${midX - p.w * 0.18} ${midY} ${next.x - next.w * 0.18} ${next.y}`}
              strokeLinecap="butt"
              strokeWidth={Math.max(1.4, p.w * 0.26)}
            />
            {/* Mắt đốt trúc hơi phình tự nhiên ở điểm nối */}
            {idx > 0 ? (
              <g>
                <line
                  className="stroke-success-bg"
                  strokeLinecap="round"
                  strokeWidth="2.4"
                  x1={p.x - p.w * 0.68}
                  x2={p.x + p.w * 0.68}
                  y1={p.y + 1.5}
                  y2={p.y - 1.5}
                />
                <line
                  className="stroke-success"
                  strokeLinecap="round"
                  strokeWidth="3.2"
                  x1={p.x - p.w * 0.76}
                  x2={p.x + p.w * 0.76}
                  y1={p.y - 1}
                  y2={p.y - 4}
                />
              </g>
            ) : null}
          </g>
        )
      })}

      {/* Các chùm lá trúc RỦ XUỐNG mọc từ các mắt đốt từ giữa thân lên đến ngọn */}
      {pts.slice(2).map((p, idx) => {
        const flipBranch = idx % 2 === 1
        const scale = 0.78 + (idx % 3) * 0.14
        return (
          <WeepingLeafBunch
            key={`leaf-${p.t}`}
            scale={scale}
            transform={`translate(${p.x}, ${p.y}) scale(${flipBranch ? -0.85 : 1}, 1) rotate(${flipBranch ? -12 : 8})`}
          />
        )
      })}
    </g>
  )
}

/** Khóm trúc uốn cong mềm mại và rủ lá dọc hai bên màn hình. */
function WeepingBambooGrove({ className, flip = false }: { className?: string; flip?: boolean }) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      focusable="false"
      preserveAspectRatio="xMinYMax slice"
      viewBox="0 0 340 700"
    >
      <g transform={flip ? 'translate(340, 0) scale(-1, 1)' : undefined}>
        {/* Lớp trúc mờ phía xa — uốn cong rộng */}
        <WeepingBambooStalk baseW={7.5} bend={145} op="opacity-30" startX={96} />
        <WeepingBambooStalk baseW={6.5} bend={175} op="opacity-25" startX={128} />
        <WeepingBambooStalk baseW={8.0} bend={95} op="opacity-35" startX={22} />

        {/* Lớp trúc trung cảnh */}
        <WeepingBambooStalk baseW={9.5} bend={130} op="opacity-60" startX={74} />
        <WeepingBambooStalk baseW={8.5} bend={160} op="opacity-50" startX={108} />

        {/* Lớp trúc tiền cảnh — thân trúc đậm, uốn cong và rủ tán lá dày */}
        <WeepingBambooStalk baseW={12} bend={105} op="opacity-95" startX={34} />
        <WeepingBambooStalk baseW={10.5} bend={135} op="opacity-85" startX={56} />

        {/* Cành trúc rủ mềm từ trên đỉnh buông xuống */}
        <path
          className="fill-none stroke-success opacity-80"
          d="M -10 8 Q 110 18 220 68 Q 275 94 310 138"
          strokeLinecap="round"
          strokeWidth="3.8"
        />
        <WeepingLeafBunch scale={1.1} transform="translate(86, 22) rotate(6)" />
        <WeepingLeafBunch scale={1.15} transform="translate(168, 48) rotate(12)" />
        <WeepingLeafBunch scale={1.0} transform="translate(248, 86) rotate(18)" />
      </g>
    </svg>
  )
}

/**
 * Chú gấu trúc đeo tai nghe chụp tai, nhắm mắt phiêu nhạc & ca hát (vẽ theo đúng mẫu ảnh mới):
 * - Đeo headphone xanh nhạt có dây buông xuống đất
 * - Đỉnh đầu có chỏm lông nhỏ
 * - Hai mắt nhắm vòng cung (^ ^), má hồng, miệng há to ca hát lộ lưỡi hồng
 * - Hai tay khum trước ngực, hai bàn chân có đệm lòng bàn chân + 3 đệm ngón chân
 * - Đuôi nhỏ ló bên phải và chùm nốt nhạc (♪ ♫) bay bên trái
 */
function HeadphoneSingingPanda({ className }: { className?: string }) {
  return (
    <svg aria-hidden="true" className={className} focusable="false" viewBox="0 0 310 280">
      {/* Chùm nốt nhạc bay bên trái đầu gấu */}
      <g className="fill-ink stroke-ink" strokeLinecap="round" strokeLinejoin="round">
        {/* Nốt đôi cao trên cùng */}
        <g transform="translate(46, 52) rotate(-12)">
          <ellipse cx="4" cy="18" rx="4.5" ry="3.2" transform="rotate(-20 4 18)" />
          <ellipse cx="18" cy="15" rx="4.5" ry="3.2" transform="rotate(-20 18 15)" />
          <path className="fill-none" d="M 8 17 L 8 2 L 22 0 L 22 14" strokeWidth="2.8" />
        </g>
        {/* Nốt đôi lớn ở giữa */}
        <g transform="translate(26, 78) rotate(-8)">
          <ellipse cx="5" cy="24" rx="5.8" ry="4.2" transform="rotate(-20 5 24)" />
          <ellipse cx="23" cy="20" rx="5.8" ry="4.2" transform="rotate(-20 23 20)" />
          <path
            className="fill-none"
            d="M 10 22 L 10 2 L 28 -2 L 28 18 M 10 8 L 28 4"
            strokeWidth="3.4"
          />
        </g>
        {/* Nốt đơn nhỏ bên trong */}
        <g transform="translate(62, 86) rotate(10)">
          <ellipse cx="4" cy="15" rx="4" ry="2.8" transform="rotate(-20 4 15)" />
          <path className="fill-none" d="M 7.5 14 L 7.5 1 Q 14 4 14 9" strokeWidth="2.4" />
        </g>
        {/* Nốt đôi phía dưới */}
        <g transform="translate(24, 126) rotate(-14)">
          <ellipse cx="4" cy="20" rx="5" ry="3.6" transform="rotate(-20 4 20)" />
          <ellipse cx="19" cy="17" rx="5" ry="3.6" transform="rotate(-20 19 17)" />
          <path
            className="fill-none"
            d="M 8.5 19 L 8.5 2 L 23.5 -1 L 23.5 16 M 8.5 7 L 23.5 4"
            strokeWidth="3"
          />
        </g>
        {/* Nốt đơn dưới cùng */}
        <g transform="translate(46, 158) rotate(8)">
          <ellipse cx="5" cy="16" rx="4.8" ry="3.4" transform="rotate(-20 5 16)" />
          <path className="fill-none" d="M 9 15 L 9 1 Q 16 4 15 10" strokeWidth="2.8" />
        </g>
      </g>

      {/* Củ tai nghe bên trái (ló ra phía sau má trái) */}
      <ellipse
        className="fill-info-bg stroke-ink"
        cx="74"
        cy="126"
        rx="14"
        ry="24"
        transform="rotate(10 74 126)"
        strokeWidth="4.5"
      />
      <ellipse
        className="fill-info opacity-45"
        cx="72"
        cy="126"
        rx="8"
        ry="17"
        transform="rotate(10 72 126)"
      />

      {/* Hai tai gấu trúc tròn đen */}
      <circle
        className="fill-ink-secondary stroke-ink"
        cx="94"
        cy="62"
        r="23"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />
      <circle
        className="fill-ink-secondary stroke-ink"
        cx="198"
        cy="64"
        r="24"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />

      {/* Đuôi tròn nhỏ ló ra phía sau bên phải */}
      <circle
        className="fill-ink-secondary stroke-ink"
        cx="196"
        cy="222"
        r="11"
        strokeWidth="4.5"
      />

      {/* Thân ngồi bầu tròn + khoang vai đen */}
      <path
        className="fill-ink-secondary stroke-ink"
        d="M 98 158 C 82 174, 80 212, 94 236 L 192 236 C 204 214, 202 174, 188 158 Z"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />

      {/* Bụng trắng tròn + bóng đổ nhẹ cạnh phải */}
      <path
        className="fill-surface stroke-ink"
        d="M 102 184 C 94 202, 98 228, 114 238 L 172 238 C 188 228, 190 202, 182 184 C 160 190, 124 190, 102 184 Z"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />
      <path
        className="fill-info-bg opacity-85"
        d="M 158 188 C 174 200, 174 222, 158 235 L 172 235 C 184 224, 186 202, 176 186 Z"
      />

      {/* Vành chụp đầu của tai nghe (Headband) ôm vòng qua đỉnh đầu */}
      <path
        className="fill-surface stroke-ink"
        d="M 94 74 C 118 32, 178 32, 212 96 L 222 92 C 186 20, 114 20, 84 68 Z"
        strokeLinejoin="round"
        strokeWidth="4.5"
      />
      <path
        className="fill-info opacity-40"
        d="M 102 68 C 124 34, 176 34, 208 92 L 216 88 C 182 26, 120 26, 94 64 Z"
      />

      {/* Đầu gấu trúc bầu phính kèm chỏm lông nhỏ trên đỉnh đầu */}
      <path
        className="fill-surface stroke-ink"
        d="M 78 96 C 80 62, 110 50, 134 50 C 132 42, 140 38, 146 44 C 150 40, 156 44, 154 50 C 184 52, 210 70, 212 104 C 216 136, 204 162, 170 168 C 144 172, 116 172, 92 164 C 68 156, 72 126, 78 96 Z"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />
      {/* Bóng đổ nhẹ bên má phải & cằm dưới */}
      <path
        className="fill-info-bg opacity-85"
        d="M 196 86 C 206 116, 198 148, 168 160 C 142 168, 110 166, 86 156 C 108 168, 148 170, 174 164 C 202 156, 210 126, 202 94 Z"
      />

      {/* Hai hàng lông mày nhỏ */}
      <path
        className="fill-none stroke-ink"
        d="M 98 82 Q 104 77 110 80 M 156 80 Q 162 77 168 82"
        strokeLinecap="round"
        strokeWidth="4"
      />

      {/* Khoang mắt trái + mắt nhắm cong vui vẻ (^ ) */}
      <ellipse
        className="fill-ink-secondary"
        cx="100"
        cy="108"
        rx="17"
        ry="20"
        transform="rotate(-12 100 108)"
      />
      <path
        className="fill-none stroke-ink"
        d="M 90 110 Q 100 98 110 110"
        strokeLinecap="round"
        strokeWidth="4.5"
      />

      {/* Khoang mắt phải + mắt nhắm cong vui vẻ ( ^) */}
      <ellipse
        className="fill-ink-secondary"
        cx="164"
        cy="108"
        rx="19"
        ry="21"
        transform="rotate(14 164 108)"
      />
      <path
        className="fill-none stroke-ink"
        d="M 153 110 Q 164 98 175 110"
        strokeLinecap="round"
        strokeWidth="4.5"
      />

      {/* Hai má hồng bầu dục */}
      <ellipse className="fill-danger opacity-55" cx="88" cy="128" rx="10" ry="4.8" />
      <ellipse className="fill-danger opacity-55" cx="174" cy="128" rx="11" ry="5" />

      {/* Mũi nhỏ + Miệng há to ca hát lộ lưỡi hồng */}
      <ellipse className="fill-ink" cx="130" cy="112" rx="7" ry="4.5" />
      <path
        className="fill-none stroke-ink"
        d="M 120 119 Q 125 123 130 118 Q 135 123 140 119"
        strokeLinecap="round"
        strokeWidth="3.6"
      />
      <path
        className="fill-danger stroke-ink"
        d="M 121 120 Q 130 123 139 120 C 141 138, 137 150, 130 150 C 123 150, 119 138, 121 120 Z"
        strokeLinejoin="round"
        strokeWidth="4"
      />
      <path
        className="fill-brand-100"
        d="M 123 134 C 127 130, 133 130, 137 134 C 136 144, 133 147, 130 147 C 127 147, 124 144, 123 134 Z"
      />

      {/* Củ tai nghe bên phải (nổi rõ 2 tầng elip) + Dây tai nghe buông xuống mặt đất */}
      <path
        className="fill-none stroke-ink"
        d="M 218 152 C 218 192, 222 232, 240 240 C 262 246, 258 236, 232 238 C 214 240, 210 246, 248 246"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="3.8"
      />
      <ellipse
        className="fill-surface stroke-ink"
        cx="212"
        cy="126"
        rx="18"
        ry="27"
        strokeWidth="4.8"
      />
      <ellipse
        className="fill-info-bg stroke-ink"
        cx="218"
        cy="126"
        rx="14"
        ry="22"
        strokeWidth="4.2"
      />
      <ellipse className="fill-info opacity-45" cx="220" cy="126" rx="9" ry="15" />

      {/* Hai tay trước khum lên trước ngực giữ nhịp */}
      <path
        className="fill-ink-secondary stroke-ink"
        d="M 102 162 C 88 172, 86 196, 102 202 C 118 206, 128 186, 122 168 C 118 158, 108 156, 102 162 Z"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />
      <path
        className="fill-ink-secondary stroke-ink"
        d="M 178 162 C 192 172, 194 196, 178 202 C 162 206, 152 186, 158 168 C 162 158, 172 156, 178 162 Z"
        strokeLinejoin="round"
        strokeWidth="4.8"
      />

      {/* Hai bàn chân ngồi phía trước có đệm lòng bàn chân + 3 đốm đệm ngón chân */}
      {/* Bàn chân trái */}
      <g transform="translate(88, 224) rotate(-10)">
        <ellipse
          className="fill-ink-secondary stroke-ink"
          cx="0"
          cy="0"
          rx="18"
          ry="22"
          strokeWidth="4.8"
        />
        <ellipse
          className="fill-surface stroke-ink"
          cx="-2"
          cy="5"
          rx="7.5"
          ry="8.5"
          strokeWidth="3.2"
        />
        <circle className="fill-surface stroke-ink" cx="-9" cy="-8" r="3.4" strokeWidth="2.6" />
        <circle className="fill-surface stroke-ink" cx="-1" cy="-11" r="3.4" strokeWidth="2.6" />
        <circle className="fill-surface stroke-ink" cx="7" cy="-7" r="3.4" strokeWidth="2.6" />
      </g>
      {/* Bàn chân phải */}
      <g transform="translate(166, 226) rotate(-6)">
        <ellipse
          className="fill-ink-secondary stroke-ink"
          cx="0"
          cy="0"
          rx="18"
          ry="22"
          strokeWidth="4.8"
        />
        <ellipse
          className="fill-surface stroke-ink"
          cx="-2"
          cy="5"
          rx="7.5"
          ry="8.5"
          strokeWidth="3.2"
        />
        <circle className="fill-surface stroke-ink" cx="-9" cy="-8" r="3.4" strokeWidth="2.6" />
        <circle className="fill-surface stroke-ink" cx="-1" cy="-11" r="3.4" strokeWidth="2.6" />
        <circle className="fill-surface stroke-ink" cx="7" cy="-7" r="3.4" strokeWidth="2.6" />
      </g>
    </svg>
  )
}

/** Chú gấu nhỏ bám ở góc khung form. */
function CornerPeekingPanda({ className, flipX = false }: { className?: string; flipX?: boolean }) {
  return (
    <svg aria-hidden="true" className={className} focusable="false" viewBox="0 0 140 120">
      <g transform={flipX ? 'translate(140, 0) scale(-1, 1)' : undefined}>
        <circle className="fill-ink-secondary stroke-ink" cx="34" cy="28" r="15" strokeWidth="4" />
        <circle className="fill-ink-secondary stroke-ink" cx="106" cy="26" r="15" strokeWidth="4" />
        <path
          className="fill-surface stroke-ink"
          d="M 26 46 C 28 18, 112 18, 114 46 C 118 72, 112 92, 92 98 C 76 102, 64 102, 48 98 C 28 92, 22 72, 26 46 Z"
          strokeLinejoin="round"
          strokeWidth="4"
        />
        <ellipse
          className="fill-ink-secondary"
          cx="48"
          cy="60"
          rx="12"
          ry="15"
          transform="rotate(-14 48 60)"
        />
        <ellipse className="fill-ink" cx="48" cy="59" rx="6" ry="7.5" />
        <circle className="fill-surface" cx="46" cy="56" r="2.6" />
        <ellipse
          className="fill-ink-secondary"
          cx="92"
          cy="60"
          rx="12"
          ry="15"
          transform="rotate(14 92 60)"
        />
        <path
          className="fill-none stroke-ink"
          d="M 97 55 L 86 60 L 97 64"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="3.5"
        />
        <ellipse className="fill-ink" cx="70" cy="65" rx="6.5" ry="4.2" />
        <path
          className="fill-ink stroke-ink"
          d="M 61 71 Q 70 76 79 71 C 80 81, 75 86, 70 86 C 65 86, 60 81, 61 71 Z"
          strokeLinejoin="round"
          strokeWidth="3"
        />
        <path
          className="fill-danger"
          d="M 64 79 C 67 76, 73 76, 76 79 C 75 84, 72 85, 70 85 C 68 85, 65 83, 64 79 Z"
        />
        <ellipse
          className="fill-ink-secondary stroke-ink"
          cx="36"
          cy="98"
          rx="14"
          ry="10"
          strokeWidth="4"
        />
        <ellipse
          className="fill-ink-secondary stroke-ink"
          cx="104"
          cy="98"
          rx="14"
          ry="10"
          strokeWidth="4"
        />
      </g>
    </svg>
  )
}

/** 4 chú gấu trúc nhỏ bám ở 4 góc của khung form. */
export function CardCornerPandas() {
  return (
    <div aria-hidden="true" className="pointer-events-none">
      <CornerPeekingPanda className="absolute -left-6 -top-11 z-20 w-20 -rotate-12 sm:-left-8 sm:-top-12 sm:w-24" />
      <CornerPeekingPanda
        className="absolute -right-6 -top-11 z-20 w-20 rotate-12 sm:-right-8 sm:-top-12 sm:w-24"
        flipX
      />
      <CornerPeekingPanda className="absolute -bottom-10 -left-6 z-20 w-20 rotate-6 sm:-bottom-11 sm:-left-8 sm:w-24" />
      <CornerPeekingPanda
        className="absolute -bottom-10 -right-6 z-20 w-20 -rotate-6 sm:-bottom-11 sm:-right-8 sm:w-24"
        flipX
      />
    </div>
  )
}

/**
 * Cột bên phải:
 * - Đã XÓA bụi trúc phía sau lưng chú gấu lớn.
 * - Thay bằng chú gấu đeo tai nghe phiêu nhạc với kích thước lớn hơn (w-full max-w-md xl:max-w-lg)
 *   và lùi xuống dưới để cân đối với background.
 */
export function RightGiantPandaScene() {
  return (
    <div className="relative flex w-full items-end justify-center pt-16 lg:mt-16 lg:translate-y-8">
      <HeadphoneSingingPanda className="relative z-10 w-full max-w-md drop-shadow-md xl:max-w-lg" />
    </div>
  )
}

/** Lá trúc rơi cong mềm tự nhiên. */
function BambooLeafTwig({ className, triple = false }: { className?: string; triple?: boolean }) {
  if (triple) {
    return (
      <svg aria-hidden="true" className={className} focusable="false" viewBox="0 0 64 46">
        <path className="fill-none stroke-success" d="M 4 8 Q 24 14 42 28" strokeWidth="1.4" />
        <path d="M 18 12 C 14 24, 14 36, 18 46 C 24 34, 22 22, 18 12 Z" />
        <path d="M 28 18 C 30 30, 36 40, 46 46 C 42 34, 36 24, 28 18 Z" />
        <path d="M 34 22 C 44 26, 54 34, 62 44 C 52 32, 44 24, 34 22 Z" />
      </svg>
    )
  }
  return (
    <svg aria-hidden="true" className={className} focusable="false" viewBox="0 0 56 24">
      <path d="M 0 4 C 16 2, 36 8, 56 22 C 34 18, 14 12, 0 4 Z" />
    </svg>
  )
}

const LEAVES = [
  { left: '2%', top: '8%', duration: 12500, offset: 800, sway: 28, size: 'w-7', triple: true },
  { left: '5%', top: '42%', duration: 14200, offset: 5200, sway: -24, size: 'w-6', triple: false },
  { left: '8%', top: '72%', duration: 11800, offset: 8600, sway: 22, size: 'w-5', triple: false },
  { left: '62%', top: '16%', duration: 13600, offset: 2100, sway: 32, size: 'w-7', triple: true },
  { left: '68%', top: '54%', duration: 15000, offset: 6900, sway: -28, size: 'w-6', triple: false },
  { left: '73%', top: '28%', duration: 11400, offset: 3700, sway: 26, size: 'w-5', triple: false },
  { left: '78%', top: '76%', duration: 13900, offset: 9800, sway: -30, size: 'w-7', triple: true },
  { left: '84%', top: '12%', duration: 12800, offset: 1400, sway: 25, size: 'w-6', triple: false },
  { left: '89%', top: '46%', duration: 14600, offset: 7400, sway: -26, size: 'w-5', triple: false },
  { left: '93%', top: '66%', duration: 11900, offset: 4300, sway: 28, size: 'w-7', triple: true },
  { left: '96%', top: '24%', duration: 13200, offset: 6100, sway: -22, size: 'w-6', triple: false },
] as const

function FallingBambooLeaves() {
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    const reduceMotion =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduceMotion) return

    const animations: Animation[] = []
    root.querySelectorAll<HTMLElement>('[data-leaf]').forEach((leaf, index) => {
      const config = LEAVES[index]
      if (!config || typeof leaf.animate !== 'function') return
      const s = config.sway
      animations.push(
        leaf.animate(
          [
            {
              top: '-6%',
              transform: 'translateX(0px) rotate(-18deg) scaleY(1)',
              opacity: 0,
            },
            {
              offset: 0.1,
              top: '10%',
              transform: `translateX(${s * 0.6}px) rotate(12deg) scaleY(0.92)`,
              opacity: 0.85,
            },
            {
              offset: 0.3,
              top: '32%',
              transform: `translateX(${s}px) rotate(34deg) scaleY(0.85)`,
              opacity: 0.9,
            },
            {
              offset: 0.52,
              top: '54%',
              transform: `translateX(${-s * 0.55}px) rotate(-14deg) scaleY(0.95)`,
              opacity: 0.85,
            },
            {
              offset: 0.76,
              top: '78%',
              transform: `translateX(${s * 0.75}px) rotate(26deg) scaleY(0.88)`,
              opacity: 0.8,
            },
            {
              offset: 0.92,
              top: '94%',
              transform: `translateX(${-s * 0.3}px) rotate(-8deg) scaleY(0.94)`,
              opacity: 0.55,
            },
            {
              top: '106%',
              transform: 'translateX(0px) rotate(18deg) scaleY(1)',
              opacity: 0,
            },
          ],
          {
            delay: -config.offset,
            duration: config.duration,
            easing: 'ease-in-out',
            iterations: Infinity,
          },
        ),
      )
    })
    return () => animations.forEach((animation) => animation.cancel())
  }, [])

  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 overflow-hidden"
      ref={rootRef}
    >
      {LEAVES.map((leaf, index) => (
        <span
          className="absolute"
          data-leaf=""
          key={leaf.left}
          style={{ left: leaf.left, top: leaf.top }}
        >
          <BambooLeafTwig
            className={cn(leaf.size, index % 3 === 0 ? 'fill-success opacity-65' : 'fill-success')}
            triple={leaf.triple}
          />
        </span>
      ))}
    </div>
  )
}

/**
 * Lớp nền phía sau toàn bộ nội dung (z-0):
 * - Khóm trúc uốn cong hình cánh cung mềm mại và rủ lá xuống hai bên
 * - Lá trúc rơi tự nhiên ở lớp nền (không bao giờ chèn lên mặt khung form)
 */
export function LoginBambooBackdrop() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <WeepingBambooGrove className="absolute inset-y-0 -left-2 h-full w-64 opacity-90 sm:w-80" />
      <WeepingBambooGrove
        className="absolute inset-y-0 -right-2 h-full w-64 opacity-90 sm:w-80"
        flip
      />
      <FallingBambooLeaves />
    </div>
  )
}
