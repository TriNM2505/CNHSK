import type { ReactNode } from 'react'

import { Card, HanziText, StatusBadge } from '@/shared/ui'

interface AuthSplitLayoutProps {
  aside: {
    badge?: string
    body: string
    hanzi?: string
    highlights?: readonly string[]
    title: string
  }
  children: ReactNode
}

/** Khung chung các màn xác thực K3: căn giữa, cột form + cột giới thiệu trên desktop. */
export function AuthSplitLayout({ aside, children }: AuthSplitLayoutProps) {
  const highlights = aside.highlights ?? [
    'Lộ trình từ vựng, chữ Hán và ngữ pháp HSK 1–3 rõ ràng',
    'Ôn tập đến hạn và luyện đề có giải thích theo điểm kiến thức',
    'Đồng bộ phiên đăng nhập giữa Web chính và Web game',
  ]

  return (
    <main className="mx-auto flex min-h-[calc(100vh-4rem)] w-full min-w-0 max-w-content items-center justify-center px-4 py-8 sm:px-6 lg:px-8 lg:py-12">
      <div className="grid w-full min-w-0 max-w-reading items-stretch gap-6 lg:max-w-content lg:grid-cols-12 lg:gap-8">
        <section className="flex min-w-0 flex-col justify-center lg:col-span-7">
          <Card className="mx-auto w-full max-w-reading p-5 shadow-sm sm:p-8">{children}</Card>
        </section>

        <aside
          aria-label="Giới thiệu nền tảng"
          className="hidden min-w-0 flex-col justify-between rounded-lg border border-brand-100 bg-surface-subtle p-8 lg:col-span-5 lg:flex"
        >
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <StatusBadge tone="info">{aside.badge ?? 'Hệ sinh thái CNHSK'}</StatusBadge>
              <HanziText className="text-brand-700" size="md">
                {aside.hanzi ?? '汉语'}
              </HanziText>
            </div>
            <h2 className="text-h2 text-brand-800">{aside.title}</h2>
            <p className="text-body text-ink-secondary">{aside.body}</p>
          </div>

          <ul className="mt-8 space-y-3 border-t border-brand-100 pt-6 text-body-sm text-ink">
            {highlights.map((item) => (
              <li className="flex items-start gap-2.5" key={item}>
                <span
                  aria-hidden="true"
                  className="mt-1.5 size-2 shrink-0 rounded-pill bg-brand-600"
                />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </aside>
      </div>
    </main>
  )
}
