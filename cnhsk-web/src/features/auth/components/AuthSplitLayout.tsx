import type { ReactNode } from 'react'

interface AuthSplitLayoutProps {
  aside: { title: string; body: string }
  children: ReactNode
}

/** Khung chung các màn tài khoản: cột nội dung + cột giới thiệu (ẩn dưới 1024 px). */
export function AuthSplitLayout({ aside, children }: AuthSplitLayoutProps) {
  return (
    <main className="grid min-h-[calc(100vh-4rem)] min-w-0 bg-surface lg:grid-cols-[minmax(0,3fr)_minmax(20rem,2fr)]">
      <section className="flex min-w-0 items-center justify-center px-4 py-10 sm:px-6 lg:px-10">
        <div className="w-full max-w-md">{children}</div>
      </section>
      <aside className="hidden min-w-0 items-center bg-brand-800 px-10 py-12 text-surface lg:flex">
        <div className="mx-auto max-w-md">
          <h2 className="text-h2">{aside.title}</h2>
          <p className="mt-4 text-body">{aside.body}</p>
        </div>
      </aside>
    </main>
  )
}
