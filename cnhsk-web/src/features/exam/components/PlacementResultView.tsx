import {
  ArrowRight,
  BarChart3,
  BookOpen,
  ClipboardList,
  Headphones,
  Languages,
  Layers,
  LayoutGrid,
  ListChecks,
  Mic,
  Pencil,
  Target,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import type { KnowledgeDomain } from '@/features/question-bank/types'
import { Button, Card, PageHeader } from '@/shared/ui'
import type { PlacementLevel, PlacementResult } from '../types'
import { PANDA_SCHOLAR_DATA_URI } from './pandaScholarArtData'

const levels: Record<PlacementLevel, string> = {
  BELOW_1: 'Chưa tới HSK 1',
  HSK1: 'HSK 1',
  HSK2: 'HSK 2',
  HSK3: 'HSK 3',
  ABOVE_3: 'HSK 3 trở lên',
}

const DOMAIN_ICONS: Record<KnowledgeDomain, LucideIcon> = {
  NA: Headphones,
  CH: BookOpen,
  TV: Mic,
  NP: Pencil,
  NL: Languages,
  VB: LayoutGrid,
}

const DOMAIN_LABELS: Record<KnowledgeDomain, string> = {
  NA: 'Nghe',
  CH: 'Đọc hiểu',
  TV: 'Nói',
  NP: 'Viết',
  NL: 'Ngữ pháp',
  VB: 'Từ vựng',
}

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-placeres-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-placeres-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-placeres-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-placeres-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-placeres-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-placeres-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

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

      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

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
  )
}

function StarMedalIcon() {
  return (
    <svg aria-hidden="true" className="size-8" fill="none" viewBox="0 0 36 36">
      {/* Dải ruy băng đỏ bên dưới */}
      <path d="M12 21 L8 33 L14 30 L17 34 L17 22 Z" className="fill-brand-600" />
      <path d="M24 21 L28 33 L22 30 L19 34 L19 22 Z" className="fill-brand-700" />
      {/* Huy chương vàng cam */}
      <circle
        cx="18"
        cy="14"
        r="10.5"
        className="fill-warning stroke-warning-bg"
        strokeWidth="1.5"
      />
      <circle cx="18" cy="14" r="8" className="fill-warning-bg/40" />
      {/* Ngôi sao trắng ở tâm */}
      <path
        d="M18 8.5 L19.7 12 L23.5 12.5 L20.7 15.1 L21.4 18.9 L18 17.1 L14.6 18.9 L15.3 15.1 L12.5 12.5 L16.3 12 Z"
        fill="white"
      />
    </svg>
  )
}

function GoldLaurelBranch() {
  const leafPath = 'M0 0 C-2.4 -3.2, -2.4 -7.2, 0 -10.5 C2.4 -7.2, 2.4 -3.2, 0 0 Z'
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none h-14 w-14 shrink-0"
      fill="#E5954B"
      viewBox="0 0 56 56"
    >
      {/* Cuống nhánh nguyệt quế mảnh uốn cong */}
      <path
        d="M14 48 C30 43, 41 29, 36 11"
        fill="none"
        stroke="#DC873B"
        strokeLinecap="round"
        strokeWidth="1.35"
      />
      {/* Hàng lá bên trong (hướng lên trái) */}
      <path d={leafPath} fill="#EB9E58" transform="translate(20, 45) rotate(22)" />
      <path d={leafPath} fill="#EB9E58" transform="translate(26.5, 40.5) rotate(10)" />
      <path d={leafPath} fill="#EB9E58" transform="translate(31.5, 34.5) rotate(-4)" />
      <path d={leafPath} fill="#EB9E58" transform="translate(35, 27.5) rotate(-18)" />
      <path d={leafPath} fill="#EB9E58" transform="translate(36.5, 20) rotate(-30)" />
      {/* Hàng lá bên ngoài (xoè sang phải) */}
      <path d={leafPath} fill="#E08B3E" transform="translate(20, 45.5) rotate(102)" />
      <path d={leafPath} fill="#E08B3E" transform="translate(26.5, 41) rotate(84)" />
      <path d={leafPath} fill="#E08B3E" transform="translate(32, 35) rotate(66)" />
      <path d={leafPath} fill="#E08B3E" transform="translate(35.5, 28) rotate(48)" />
      <path d={leafPath} fill="#E08B3E" transform="translate(37, 20.5) rotate(30)" />
      {/* Lá đỉnh ngọn */}
      <path d={leafPath} fill="#E5954B" transform="translate(36, 12.5) rotate(-18)" />
    </svg>
  )
}

function GreenBambooCornerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute right-0 top-0 h-24 w-40 text-brand-700"
      fill="currentColor"
      viewBox="0 0 160 96"
    >
      {/* Hai thân trúc đỏ nghiêng từ trên trái xuống dưới phải */}
      <path
        d="M104 -4 L148 92"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="4.2"
      />
      <line stroke="#FFF1F2" strokeWidth="1.8" x1="117" x2="124" y1="30" y2="27" />
      <line stroke="#FFF1F2" strokeWidth="1.8" x1="131" x2="138" y1="60" y2="57" />

      <path
        d="M122 -4 L158 72"
        fill="none"
        stroke="#DC2626"
        strokeLinecap="round"
        strokeWidth="3.4"
      />
      <line stroke="#FFF1F2" strokeWidth="1.6" x1="133" x2="139" y1="24" y2="21" />
      <line stroke="#FFF1F2" strokeWidth="1.6" x1="145" x2="151" y1="50" y2="47" />

      {/* Nhánh trúc vươn sang trái */}
      <path
        d="M114 16 Q88 18 64 28"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.6"
      />

      {/* Các phiến lá trúc tông đỏ đồng bộ */}
      <path d="M84 14 Q52 4 22 6 Q54 16 84 14 Z" fill="#DC2626" />
      <path d="M82 16 Q50 20 26 36 Q58 28 82 16 Z" fill="currentColor" />
      <path d="M98 22 Q74 24 54 36 Q78 34 98 22 Z" fill="#DC2626" />
      <path d="M98 24 Q76 40 58 60 Q84 44 98 24 Z" fill="currentColor" />
      <path d="M102 26 Q106 44 114 62 Q112 42 102 26 Z" fill="#DC2626" />
      <path d="M116 8 Q122 26 130 42 Q128 22 116 8 Z" fill="#991B1B" />
      <path d="M134 4 Q142 16 148 28 Q144 12 134 4 Z" fill="#DC2626" />
    </svg>
  )
}

function PandaScholarArt() {
  return (
    <img
      alt=""
      aria-hidden="true"
      className="mx-auto h-48 w-auto select-none object-contain"
      src={PANDA_SCHOLAR_DATA_URI}
    />
  )
}

function KidBambooMountainCornerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-72 text-brand-600 sm:block"
      fill="currentColor"
      preserveAspectRatio="xMaxYMax slice"
      viewBox="0 0 288 88"
    >
      <path d="M12 88 L76 44 L128 70 L188 30 L248 66 L288 46 L288 88 Z" opacity="0.16" />
      <path d="M96 88 C144 58, 196 52, 244 70 C262 58, 276 56, 288 64 L288 88 Z" opacity="0.28" />
      <g className="text-brand-700" opacity="0.9">
        <path d="M276 90 L256 -6" fill="none" stroke="currentColor" strokeWidth="5" />
        <line stroke="white" strokeWidth="1.8" x1="264" x2="271" y1="36" y2="35" />
        <line stroke="white" strokeWidth="1.8" x1="270" x2="277" y1="68" y2="67" />

        <path d="M262 90 L236 -4" fill="none" stroke="currentColor" strokeWidth="3.8" />
        <line stroke="white" strokeWidth="1.6" x1="244" x2="250" y1="28" y2="27" />
        <line stroke="white" strokeWidth="1.6" x1="253" x2="259" y1="60" y2="59" />

        <path
          d="M248 10 Q216 6 190 18"
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.6"
        />
        <path d="M234 10 Q212 18 198 32 Q220 24 234 10 Z" />
        <path d="M244 8 Q230 24 222 42 Q238 28 244 8 Z" />
        <path d="M220 12 Q198 10 182 18 Q202 20 220 12 Z" opacity="0.85" />
        <path d="M250 28 Q230 38 218 54 Q238 44 250 28 Z" opacity="0.9" />
        <path d="M236 32 Q216 38 204 50 Q224 44 236 32 Z" opacity="0.78" />
      </g>
    </svg>
  )
}

export function PlacementResultView({
  result,
  onRetake,
  pending,
  error,
}: {
  result: PlacementResult
  onRetake: () => void
  pending?: boolean
  error?: string
}) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:max-w-[1072px]">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          subtitle="Đánh giá cấp độ HSK phù hợp và hồ sơ sáu miền năng lực."
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Kết quả xếp trình độ</span>
            </span>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>

      {/* Hai thẻ tổng quan: Trình độ & Độ tin cậy */}
      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        <Card className="flex items-center justify-between gap-4 rounded-lg border-brand-100 bg-brand-50 px-5 py-4">
          <div className="flex min-w-0 items-center gap-3.5">
            <span className="flex size-14 shrink-0 items-center justify-center rounded-pill bg-brand-100">
              <StarMedalIcon />
            </span>
            <div className="min-w-0 space-y-1">
              <p className="text-body-sm font-semibold text-ink-secondary">Trình độ</p>
              <p className="sr-only">Cấp ước lượng: {levels[result.level]}</p>
              <p
                aria-hidden="true"
                className=" flex flex-wrap items-center gap-2 text-h2 font-bold text-ink"
              >
                <span>Cấp ước lượng:</span>
                <span className="inline-flex items-center rounded-pill bg-brand-700 px-3.5 py-0.5 text-h3 font-bold text-surface">
                  {levels[result.level]}
                </span>
              </p>
            </div>
          </div>
          <GoldLaurelBranch />
        </Card>

        <Card className="flex items-center justify-between gap-4 rounded-lg border-brand-100 bg-brand-50 px-5 py-4">
          <div className="flex min-w-0 items-center gap-3.5">
            <span className="flex size-14 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
              <ClipboardList aria-hidden="true" className="size-7" />
            </span>
            <div className="min-w-0 space-y-1">
              <p className="text-body-sm font-semibold text-ink-secondary">Độ tin cậy</p>
              <p className="text-h2 font-bold text-brand-700">
                {result.confidence === 'SURE' ? 'Chắc chắn' : 'Kết quả tạm'}
              </p>
            </div>
          </div>
          <GoldLaurelBranch />
        </Card>
      </div>

      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}

      {/* Khối nút thao tác + Bảng kết quả theo cấp + Minh hoạ Gấu trúc HSK */}
      <div className="grid min-w-0 items-end gap-6 lg:grid-cols-[minmax(0,1fr)_270px]">
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap gap-3">
            <Button className="px-5" disabled={pending} onClick={onRetake}>
              <BarChart3 aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
              Làm lại
            </Button>
            <Button className="bg-surface px-5" to="/learn/exams" variant="outline">
              <ListChecks
                aria-hidden="true"
                className="mr-1.5 inline-block size-4 align-middle text-ink-secondary"
              />
              Danh sách đề thi
            </Button>
          </div>

          <div className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 p-5 sm:p-6">
            <GreenBambooCornerArt />
            <div className="relative z-nav overflow-x-auto">
              <table className="w-full border-separate border-spacing-0 text-left text-body-sm">
                <caption className="mb-3 text-left text-h3 font-bold text-ink">
                  <span className="inline-flex items-center gap-2">
                    <BarChart3 aria-hidden="true" className="size-5 text-brand-700" />
                    <span>Kết quả theo cấp</span>
                  </span>
                </caption>
                <thead>
                  <tr className="bg-brand-100/70 text-ink">
                    <th className="rounded-tl-md px-4 py-2.5 font-semibold" scope="col">
                      Cấp
                    </th>
                    <th className="px-4 py-2.5 text-center font-semibold" scope="col">
                      Số câu đúng
                    </th>
                    <th className="rounded-tr-md px-4 py-2.5 text-center font-semibold" scope="col">
                      Tỷ lệ
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-surface">
                  {result.byLevel.map((row) => (
                    <tr key={row.level}>
                      <td className="rounded-bl-md border-t border-brand-100/60 px-4 py-3">
                        <span className="inline-flex items-center rounded-pill bg-brand-100 px-3 py-0.5 text-body-sm font-bold text-brand-700">
                          {`HSK ${row.level}`}
                        </span>
                      </td>
                      <td className="border-t border-brand-100/60 px-4 py-3 text-center text-body font-semibold tabular-nums text-ink">
                        {`${row.correct}/${row.total}`}
                      </td>
                      <td className="rounded-br-md border-t border-brand-100/60 px-4 py-3 text-center text-body font-bold tabular-nums text-brand-700">
                        {`${row.percent}%`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div className="hidden lg:block">
          <PandaScholarArt />
        </div>
      </div>

      {/* Hồ sơ sáu miền */}
      <section aria-label="Hồ sơ sáu miền" className="space-y-4">
        <h2 className="flex items-center gap-2.5 text-h2 text-ink">
          <Layers aria-hidden="true" className="size-5 text-brand-700" />
          <span>Hồ sơ sáu miền</span>
        </h2>
        <ul className="grid min-w-0 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {result.domains.map(({ domain, measured, correct, total, percent, tentative }) => {
            const DomainIcon = DOMAIN_ICONS[domain] ?? Layers
            const barPercent = measured
              ? Math.min(
                  Math.max(percent ?? (total > 0 ? Math.round((correct / total) * 100) : 0), 0),
                  100,
                )
              : 0

            return (
              <li
                key={domain}
                className="flex min-w-0 items-start gap-3.5 rounded-lg border border-brand-100 bg-brand-50 p-5"
              >
                <span className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <DomainIcon aria-hidden="true" className="size-5" />
                </span>
                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex items-baseline gap-2">
                    <h3 className="text-h3 font-bold text-ink">{domain}</h3>
                    <span
                      aria-hidden="true"
                      className="text-body-sm font-medium text-ink-secondary"
                    >
                      {DOMAIN_LABELS[domain]}
                    </span>
                  </div>
                  <p className="text-body-sm font-medium text-ink">
                    {measured
                      ? correct + '/' + total + (percent === null ? '' : ' (' + percent + '%)')
                      : 'Chưa đo'}
                    {tentative && <span> · tạm</span>}
                  </p>
                  <div
                    aria-hidden="true"
                    className="h-2 w-full overflow-hidden rounded-pill bg-line/70"
                  >
                    <div
                      className="h-full rounded-pill bg-brand-600 transition-all"
                      style={{ width: `${barPercent}%` }}
                    />
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      </section>

      {/* Câu sai theo KID */}
      {result.wrongKids.length > 0 && (
        <section
          aria-label="Câu sai theo KID"
          className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 px-5 py-4 sm:px-6"
        >
          <KidBambooMountainCornerArt />
          <div className="relative z-nav flex flex-wrap items-center gap-4 sm:pr-28">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
              <Target aria-hidden="true" className="size-5" />
            </span>
            <h2 className="text-h3 text-ink">Câu sai theo KID</h2>
            <ul className="flex flex-wrap items-center gap-x-4 gap-y-2 text-body-sm text-ink-secondary">
              {result.wrongKids.map(({ kid, nameVi, wrong, total }) => (
                <li key={kid} className="inline-flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>
                    {nameVi} ({kid}) · {wrong}/{total} câu sai
                  </span>
                  <Link
                    className="inline-flex min-h-touch items-center gap-1 text-label font-semibold text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                    to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                  >
                    <span>Luyện nút này</span>
                    <ArrowRight aria-hidden="true" className="size-4" />
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}
    </main>
  )
}
