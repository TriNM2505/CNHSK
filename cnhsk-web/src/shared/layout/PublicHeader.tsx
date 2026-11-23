import { ChevronDown, Lock, Menu, Search, X } from 'lucide-react'
import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { Link, useLocation, type Location } from 'react-router-dom'

import { findKnownRoute } from '@/app/routeAccess'
import { useCurrentUser } from '@/shared/auth'
import { AccountMenu } from '@/shared/layout/AccountMenu'
import { BrandLink } from '@/shared/layout/AppShell'
import { gameNavigationItem } from '@/shared/layout/navigation'
import { publicMenusFor, type PublicMenu } from '@/shared/layout/publicNavigation'
import { cn } from '@/shared/lib/cn'
import { Button, IconButton } from '@/shared/ui'

const linkClass =
  'flex min-h-touch items-center rounded-sm text-body text-ink-secondary no-underline transition-colors hover:text-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'
const itemClass =
  'inline-flex min-h-touch items-center gap-1.5 whitespace-nowrap rounded-sm px-2 text-body font-semibold text-ink no-underline transition-colors hover:text-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus xl:gap-2 xl:px-3 xl:text-lg'

/** Public pages that are not worth returning to after signing in; some carry a one-time token. */
const noReturnPaths = new Set([
  '/',
  '/403',
  '/reminder/unsubscribe',
  '/reset-password',
  '/terms',
  '/verify-email',
])

function authQuery({ hash, pathname, search }: Location): string {
  const carried = new URLSearchParams(search).get('redirect')
  if (carried) return `?redirect=${encodeURIComponent(carried)}`
  if (noReturnPaths.has(pathname) || findKnownRoute(pathname)?.access !== 'public') return ''
  return `?redirect=${encodeURIComponent(`${pathname}${search}${hash}`)}`
}

function MenuColumns({ className, menu }: { className?: string; menu: PublicMenu }) {
  return (
    <>
      <div className={cn('grid gap-x-8 gap-y-6', className)}>
        {menu.columns.map((column) => (
          <div className="min-w-0" key={column.title}>
            <p className="border-b border-line pb-3 text-label uppercase text-ink">{column.title}</p>
            <ul className="mt-2">
              {column.links.map((link) => (
                <li key={link.label}>
                  <Link className={linkClass} to={link.to}>
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      {menu.note ? (
        <p className="mt-4 flex items-center gap-2 text-body-sm text-ink-muted">
          <Lock aria-hidden="true" className="size-4 shrink-0" />
          {menu.note}
        </p>
      ) : null}
    </>
  )
}

export function PublicHeader({ gameOrigin = import.meta.env.VITE_GAME_ORIGIN }: { gameOrigin?: string }) {
  const location = useLocation()
  const { user } = useCurrentUser()
  const header = useRef<HTMLElement>(null)
  const closing = useRef<number>()
  /** `pinned` menus were opened by click or keyboard and stay until closed on purpose. */
  const [open, setOpen] = useState<{ id: string; pinned: boolean }>()
  const [mobileOpen, setMobileOpen] = useState(false)
  const publicMenus = publicMenusFor(user)
  const menu = publicMenus.find(({ id }) => id === open?.id)
  const query = authQuery(location)

  useEffect(() => {
    setOpen(undefined)
    setMobileOpen(false)
  }, [location.key])
  useEffect(() => () => window.clearTimeout(closing.current), [])
  useEffect(() => {
    if (!open && !mobileOpen) return
    function closeWithEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      header.current?.querySelector<HTMLElement>('[aria-expanded="true"]')?.focus()
      setOpen(undefined)
      setMobileOpen(false)
    }
    function closeOutside(event: Event) {
      if (event.target instanceof Node && header.current?.contains(event.target)) return
      setOpen(undefined)
      setMobileOpen(false)
    }
    document.addEventListener('keydown', closeWithEscape)
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('focusin', closeOutside)
    return () => {
      document.removeEventListener('keydown', closeWithEscape)
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('focusin', closeOutside)
    }
  }, [mobileOpen, open])

  function hover(event: PointerEvent, id?: string) {
    if (event.pointerType !== 'mouse') return
    window.clearTimeout(closing.current)
    if (id) setOpen((current) => (current?.id === id ? current : { id, pinned: false }))
  }

  function leave(event: PointerEvent) {
    if (event.pointerType !== 'mouse') return
    // A short delay lets the pointer cross the gap between the bar and the panel.
    closing.current = window.setTimeout(() => setOpen((current) => (current?.pinned ? current : undefined)), 180)
  }

  function toggle(id: string) {
    setOpen((current) => (current?.id === id && current.pinned ? undefined : { id, pinned: true }))
  }

  return (
    <header className="sticky top-0 z-20 border-b border-line bg-surface" onPointerLeave={leave} ref={header}>
      <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-4 px-4 lg:h-24">
        <BrandLink logoClassName="lg:h-14" />
        <nav aria-label="Điều hướng công khai" className="hidden lg:block">
          <ul className="flex items-center xl:gap-1">
            {publicMenus.map(({ icon: Icon, id, label }) => {
              const expanded = open?.id === id
              return (
                <li key={id}>
                  <button
                    aria-controls="public-menu"
                    aria-expanded={expanded}
                    className={cn(itemClass, expanded && 'text-brand-600')}
                    onClick={() => toggle(id)}
                    onPointerEnter={(event) => hover(event, id)}
                    type="button"
                  >
                    <Icon aria-hidden="true" className="hidden size-5 xl:block" />
                    {label}
                    <ChevronDown aria-hidden="true" className={cn('size-4 transition-transform', expanded && 'rotate-180')} />
                  </button>
                </li>
              )
            })}
            {gameOrigin ? (
              <li>
                <a className={itemClass} href={gameOrigin} onPointerEnter={(event) => { hover(event); setOpen(undefined) }}>
                  <gameNavigationItem.icon aria-hidden="true" className="hidden size-5 xl:block" />
                  Trò chơi
                </a>
              </li>
            ) : null}
          </ul>
        </nav>
        <div className="flex items-center gap-2">
          <Link
            aria-label="Tra từ điển"
            className="hidden size-touch items-center justify-center rounded-sm text-ink transition-colors hover:text-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus lg:inline-flex"
            to="/dictionary"
          >
            <Search aria-hidden="true" className="size-6" />
          </Link>
          {user ? (
            <AccountMenu nameFrom="xl" />
          ) : (
            <nav aria-label="Tài khoản" className="flex items-center gap-2">
              <Button className="px-3 lg:text-body lg:font-semibold" to={`/login${query}`} variant="ghost">
                Đăng nhập
              </Button>
              <Button className="lg:min-h-14 lg:px-6 lg:text-body lg:font-bold" to={`/register${query}`}>
                Đăng ký
              </Button>
            </nav>
          )}
          <IconButton
            aria-controls="public-mobile-menu"
            aria-expanded={mobileOpen}
            className="lg:hidden"
            label={mobileOpen ? 'Đóng menu' : 'Mở menu'}
            onClick={() => setMobileOpen((value) => !value)}
          >
            {mobileOpen ? <X aria-hidden="true" className="size-5" /> : <Menu aria-hidden="true" className="size-5" />}
          </IconButton>
        </div>
      </div>

      {menu ? (
        <div
          className="absolute inset-x-0 top-full hidden border-t border-line bg-surface shadow-overlay lg:block"
          id="public-menu"
          onPointerEnter={(event) => hover(event)}
        >
          <div className="mx-auto max-w-content px-4 py-8">
            <MenuColumns className={menu.columns.length > 3 ? 'lg:grid-cols-4' : 'lg:grid-cols-3'} menu={menu} />
          </div>
        </div>
      ) : null}
      {mobileOpen ? (
        <nav
          aria-label="Điều hướng trên thiết bị di động"
          className="absolute inset-x-0 top-full max-h-[calc(100dvh-4rem)] overflow-y-auto border-t border-line bg-surface px-4 pb-6 shadow-overlay lg:hidden"
          id="public-mobile-menu"
        >
          {publicMenus.map((item) => (
            <details className="group border-b border-line" key={item.id}>
              <summary className="flex min-h-14 cursor-pointer list-none items-center gap-3 text-body font-semibold text-ink [&::-webkit-details-marker]:hidden">
                <item.icon aria-hidden="true" className="size-5 text-brand-600" />
                {item.label}
                <ChevronDown aria-hidden="true" className="ml-auto size-5 transition-transform group-open:rotate-180" />
              </summary>
              <div className="pb-4 pl-8">
                <MenuColumns className="sm:grid-cols-2" menu={item} />
              </div>
            </details>
          ))}
          {gameOrigin ? (
            <a className="flex min-h-14 items-center gap-3 text-body font-semibold text-ink no-underline" href={gameOrigin}>
              <gameNavigationItem.icon aria-hidden="true" className="size-5 text-brand-600" />
              Trò chơi
            </a>
          ) : null}
        </nav>
      ) : null}
    </header>
  )
}