import { Link, NavLink, Outlet } from 'react-router-dom'

import { webHref } from '../shared/ui/gameMeta'

export default function App() {
  return (
    <div className="flex min-h-screen min-w-0 flex-col">
      <a
        href="#game-content"
        className="sr-only z-overlay rounded-sm bg-surface p-3 text-ink focus:not-sr-only focus:absolute focus:inline-flex focus:min-h-touch focus:items-center"
      >
        Đến nội dung chính
      </a>
      <header className="topbar">
        <Link to="/" aria-label="CNHSK Game Box" className="brand">
          <span className="seal" aria-hidden="true">字</span>
          <span>CNHSK <b>ARCADE</b></span>
        </Link>
        <nav aria-label="Điều hướng trò chơi">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav-current' : undefined)}>
            Chọn game
          </NavLink>
          <a href={webHref('/leaderboards/games')}>Bảng xếp hạng</a>
          <a href={webHref('/learn')}>Về trang học</a>
        </nav>
      </header>
      <main id="game-content" className="flex min-w-0 flex-1 flex-col" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
