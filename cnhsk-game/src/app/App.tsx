import { Link, NavLink, Outlet } from 'react-router-dom'

import hocBaLogoWhite from '../shared/brand/assets/hoc-ba-logo-white.svg'
import { webHref } from '../shared/ui/gameMeta'

const iconProps = {
  'aria-hidden': true,
  fill: 'none',
  stroke: 'currentColor',
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  strokeWidth: 1.75,
  viewBox: '0 0 24 24',
} as const

function GridIcon() {
  return (
    <svg {...iconProps}>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.6" />
    </svg>
  )
}

function TrophyIcon() {
  return (
    <svg {...iconProps}>
      <path d="M8 4h8v5a4 4 0 0 1-8 0V4Z" />
      <path d="M8 6H5.5a2.5 2.5 0 0 0 2.6 3.4M16 6h2.5a2.5 2.5 0 0 1-2.6 3.4" />
      <path d="M12 13v3.5M9 20h6M10 16.5h4l.6 3.5H9.4l.6-3.5Z" />
    </svg>
  )
}

function ExitIcon() {
  return (
    <svg {...iconProps}>
      <path d="M8 16 16 8M10 8h6v6" />
    </svg>
  )
}

export default function App() {
  return (
    <div className="flex min-h-screen min-w-0 flex-col">
      <a
        href="#game-content"
        className="sr-only z-overlay rounded-sm bg-surface p-3 text-ink focus:not-sr-only focus:absolute focus:inline-flex focus:min-h-touch focus:items-center"
      >
        Đến nội dung chính
      </a>
      <header className="topbar game-topbar">
        <Link to="/" aria-label="Học Bá Game Box" className="game-brand">
          <img src={hocBaLogoWhite} alt="" width={80} height={53} decoding="async" className="game-brand-logo" />
          <span className="game-brand-divider" aria-hidden="true" />
          <span className="game-brand-label">Game Box</span>
        </Link>
        <div className="game-topbar-actions">
          <nav aria-label="Điều hướng trò chơi" className="game-nav">
            <NavLink to="/" end aria-label="Chọn game" className="game-nav-item">
              <GridIcon />
              <span className="game-nav-text">Chọn game</span>
            </NavLink>
            <a href={webHref('/leaderboards/games')} aria-label="Bảng xếp hạng" className="game-nav-item">
              <TrophyIcon />
              <span className="game-nav-text">Bảng xếp hạng</span>
            </a>
          </nav>
          <a href={webHref('/learn')} aria-label="Về trang học" className="game-exit">
            <span className="game-nav-text">Về trang học</span>
            <ExitIcon />
          </a>
        </div>
      </header>
      <main id="game-content" className="flex min-w-0 flex-1 flex-col" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
