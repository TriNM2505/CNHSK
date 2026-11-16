import { BookOpenText, CheckCircle2, Gamepad2 } from 'lucide-react'
import type { ReactNode } from 'react'

import { Card, HanziText, StatusBadge } from '@/shared/ui'

interface AuthSplitLayoutProps {
  aside: {
    badge?: string
    body: string
    hanzi?: string
    highlights?: readonly string[]
    meaning?: string
    pinyin?: string
    title: string
  }
  /** Trang trí bám quanh 4 góc Card form (bản thử SCR-002). */
  cardDecorator?: ReactNode
  children: ReactNode
  /** Thay toàn bộ cột giới thiệu bên phải bằng minh họa riêng (bản thử SCR-002). */
  customAside?: ReactNode
  /** Ẩn banner tóm tắt trên mobile khi dùng minh họa riêng. */
  hideMobileBanner?: boolean
  /** Thay thẻ chữ Hán mặc định ở cột phải (bản thử trang trí SCR-002). */
  illustration?: ReactNode
}

const DEFAULT_HIGHLIGHTS = [
  'Lộ trình từ vựng, chữ Hán và ngữ pháp HSK 1–3 rõ ràng',
  'Ôn tập đến hạn và luyện đề có giải thích theo điểm kiến thức',
  'Đồng bộ phiên đăng nhập giữa Web chính và Web game',
] as const

const HIGHLIGHT_ICONS = [BookOpenText, CheckCircle2, Gamepad2] as const

/** Khung chung các màn xác thực K3: căn giữa, cột form + cột thẻ từ vựng giới thiệu. */
export function AuthSplitLayout({
  aside,
  cardDecorator,
  children,
  customAside,
  hideMobileBanner = false,
  illustration,
}: AuthSplitLayoutProps) {
  const highlights = aside.highlights ?? DEFAULT_HIGHLIGHTS
  const hanzi = aside.hanzi ?? '汉语'
  const pinyin = aside.pinyin ?? 'hànyǔ'
  const meaning = aside.meaning ?? 'Tiếng Hán · Nền tảng học tiếng Trung cho người Việt'
  const badgeText = aside.badge ?? 'Hệ sinh thái CNHSK'

  return (
    <main className="relative z-10 mx-auto flex w-full min-w-0 max-w-content flex-1 items-center justify-center px-4 py-8 sm:px-6 sm:py-10 lg:px-8 lg:py-12">
      <div className="grid w-full min-w-0 max-w-reading items-center gap-6 lg:max-w-content lg:grid-cols-12 lg:gap-8">
        {hideMobileBanner ? null : (
          <div className="flex min-w-0 items-center justify-between gap-4 rounded-lg border border-brand-100 bg-surface-subtle p-4 lg:hidden">
            <div className="min-w-0 space-y-1">
              <StatusBadge tone="info">{badgeText}</StatusBadge>
              <p className="text-label text-brand-800">{aside.title}</p>
              <p className="text-caption text-ink-secondary">{meaning}</p>
            </div>
            <div className="shrink-0 rounded-md border border-brand-100 bg-surface px-3 py-2 text-center shadow-sm">
              <HanziText className="text-brand-800" size="md">
                {hanzi}
              </HanziText>
              <p className="text-caption text-ink-secondary">{pinyin}</p>
            </div>
          </div>
        )}

        <section className="flex min-w-0 flex-col justify-center lg:col-span-7">
          <div className="relative mx-auto w-full max-w-reading">
            <Card className="relative z-10 w-full rounded-lg border-line bg-surface p-5 shadow-md sm:p-8">
              {children}
            </Card>
            {cardDecorator}
          </div>
        </section>

        {customAside ? (
          <aside
            aria-label="Minh họa trang đăng nhập"
            className="hidden min-w-0 items-end justify-center self-end lg:col-span-5 lg:flex"
          >
            {customAside}
          </aside>
        ) : (
          <aside
            aria-label="Giới thiệu nền tảng"
            className="hidden min-w-0 flex-col justify-between gap-6 rounded-lg border border-brand-100 bg-surface-subtle p-8 shadow-sm lg:col-span-5 lg:flex"
          >
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <StatusBadge tone="info">{badgeText}</StatusBadge>
                <span className="text-caption text-brand-700">HSK 1–3 · Tiếng Việt</span>
              </div>
              <h2 className="text-h2 text-brand-800">{aside.title}</h2>
              <p className="text-body text-ink-secondary">{aside.body}</p>
            </div>

            {illustration ?? (
              <Card className="border-brand-100 bg-surface p-5 text-center shadow-sm">
                <HanziText className="text-brand-800" size="lg">
                  {hanzi}
                </HanziText>
                <p className="mt-1 text-h3 text-ink">{pinyin}</p>
                <p className="mt-1 text-body-sm text-ink-secondary">{meaning}</p>
              </Card>
            )}

            <ul className="space-y-2.5">
              {highlights.map((item, index) => {
                const Icon = HIGHLIGHT_ICONS[index % HIGHLIGHT_ICONS.length]!
                return (
                  <li
                    className="flex items-start gap-3 rounded-md border border-brand-100 bg-surface p-3 text-body-sm text-ink shadow-sm"
                    key={item}
                  >
                    <span
                      aria-hidden="true"
                      className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-brand-50 text-brand-700"
                    >
                      <Icon className="size-4" />
                    </span>
                    <span className="pt-1">{item}</span>
                  </li>
                )
              })}
            </ul>
          </aside>
        )}
      </div>
    </main>
  )
}
