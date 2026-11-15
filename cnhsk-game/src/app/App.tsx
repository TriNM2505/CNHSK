import { Link, NavLink, Outlet } from 'react-router-dom'

export default function App() {
  return (
    <div className="flex min-h-screen min-w-0 flex-col">
      <a
        href="#game-content"
        className="sr-only z-overlay rounded-sm bg-surface p-3 focus:not-sr-only focus:absolute focus:inline-flex focus:min-h-touch focus:items-center"
      >
        Đến nội dung chính
      </a>
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex min-h-20 max-w-content flex-wrap items-center justify-between gap-x-5 gap-y-1 px-4 py-3 sm:px-6">
          <Link to="/" aria-label="CNHSK Game Box" className="flex min-h-touch items-center gap-3">
            <span
              aria-hidden="true"
              className="flex size-11 shrink-0 items-center justify-center rounded-md bg-brand-800 font-hanzi text-h3 text-surface"
            >
              学
            </span>
            <span className="text-h3 font-bold tracking-tight text-brand-800">CNHSK Game Box</span>
          </Link>
          <nav aria-label="Điều hướng trò chơi">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                `inline-flex min-h-touch items-center rounded-sm px-3 text-label ${isActive ? 'bg-brand-50 text-brand-800' : 'text-ink-secondary hover:bg-surface-subtle'}`
              }
            >
              Danh sách trò chơi
            </NavLink>
          </nav>
        </div>
      </header>
      <main
        id="game-content"
        className="mx-auto w-full min-w-0 max-w-content flex-1 px-4 py-8 sm:px-6 sm:py-12"
        tabIndex={-1}
      >
        <Outlet />
      </main>
      <footer className="border-t border-line px-4 py-5 text-center text-body-sm text-ink-secondary">
        CNHSK · Học đều một chút mỗi ngày
      </footer>
    </div>
  )
}
