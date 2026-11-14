import {
  BookOpen,
  Flag,
  ListChecks,
  Gamepad2,
  Languages,
  Mic2,
  PenTool,
  ShieldCheck,
  Upload,
  UsersRound,
} from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'

import type { RoleCode } from '@/features/auth/types'

export type NavigationItem = {
  activePrefixes: readonly string[]
  icon: ComponentType<SVGProps<SVGSVGElement>>
  label: string
  mobileLabel?: string
  role?: RoleCode
  mobilePlacement?: 'more'
  to: string
}

export const learnerNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/learn/topics'],
    icon: BookOpen,
    label: 'Chủ đề',
    to: '/learn/topics',
  },
  {
    activePrefixes: ['/learn/grammar'],
    icon: Languages,
    label: 'Ngữ pháp',
    to: '/learn/grammar',
  },
  {
    activePrefixes: ['/learn/pronunciation'],
    icon: Mic2,
    label: 'Phát âm',
    to: '/learn/pronunciation',
  },
  {
    activePrefixes: ['/learn/characters'],
    icon: PenTool,
    label: 'Chữ Hán',
    to: '/learn/characters/recognition',
  },
  {
    activePrefixes: ['/references'],
    icon: BookOpen,
    label: 'Tài liệu tham khảo',
    mobileLabel: 'Tham khảo',
    mobilePlacement: 'more',
    to: '/references/media',
  },
]

export const learnerCommunityNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/community/quizzes'],
    icon: ListChecks,
    label: 'Quiz',
    to: '/community/quizzes',
  },
]

export const gameNavigationItem = {
  icon: Gamepad2,
  label: 'Game',
} as const

export const adminNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/content/references'],
    icon: BookOpen,
    label: 'Tài nguyên tham khảo',
    mobileLabel: 'Tham khảo',
    role: 'CONTENT_EDITOR',
    to: '/content/references',
  },
  {
    activePrefixes: ['/content/imports'],
    icon: Upload,
    label: 'Nhập dữ liệu',
    role: 'CONTENT_EDITOR',
    to: '/content/imports',
  },
  {
    activePrefixes: ['/admin/users'],
    icon: UsersRound,
    label: 'Người dùng',
    role: 'ADMIN',
    to: '/admin/users',
  },
  {
    activePrefixes: ['/moderation/posts'],
    icon: ShieldCheck,
    label: 'Bài chờ duyệt',
    role: 'MODERATOR',
    to: '/moderation/posts',
  },
  {
    activePrefixes: ['/moderation/reports'],
    icon: Flag,
    label: 'Báo cáo vi phạm',
    role: 'MODERATOR',
    to: '/moderation/reports',
  },
]

export function isNavigationItemActive(pathname: string, item: NavigationItem): boolean {
  return item.activePrefixes.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  )
}
