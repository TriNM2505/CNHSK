import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/shared/ui'

import { ContestAdminBannerArt } from './ContestAdminArt'

type Props = {
  listPath: string
  title: string
  subtitle?: string
  children?: ReactNode
}

export function ContestEditorView({ listPath, title, subtitle, children }: Props) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 break-words px-4 py-6 pb-16 sm:px-6">
      <div className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex min-w-0 items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
              <span className="min-w-0 break-words">{title}</span>
            </span>
          }
          subtitle={subtitle}
        />
        <ContestAdminBannerArt />
      </div>
      <Link className="inline-flex min-h-touch items-center text-brand-700 underline" to={listPath}>
        Về danh sách cuộc thi
      </Link>
      {children}
    </main>
  )
}
