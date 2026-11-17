import {
  BookOpen,
  FileText,
  GitBranch,
  CreditCard,
  Flag,
  Layers,
  ListChecks,
  Gamepad2,
  Languages,
  Mic2,
  PenTool,
  ReceiptText,
  Settings,
  ShieldCheck,
  Trophy,
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
  roles?: readonly RoleCode[]
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
  {
    activePrefixes: ['/grading'],
    icon: PenTool,
    label: 'Chấm bài',
    mobilePlacement: 'more',
    to: '/grading',
  },
]

export const learnerCommunityNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/community/quizzes'],
    icon: ListChecks,
    label: 'Quiz',
    to: '/community/quizzes',
  },
  {
    activePrefixes: ['/contests'],
    icon: Trophy,
    label: 'Cuộc thi',
    to: '/contests',
  },
  {
    activePrefixes: ['/leaderboards'],
    icon: Trophy,
    label: 'Bảng xếp hạng',
    mobilePlacement: 'more',
    to: '/leaderboards/topics',
  },
]

export const gameNavigationItem = {
  icon: Gamepad2,
  label: 'Game',
} as const

export const adminNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/content/ai-review'],
    icon: ListChecks,
    label: 'Duyệt câu hỏi AI',
    mobileLabel: 'Duyệt AI',
    roles: ['TEACHER', 'CONTENT_EDITOR'],
    to: '/content/ai-review',
  },
  {
    activePrefixes: ['/content/exams'],
    icon: FileText,
    label: 'Quản lý đề thi',
    mobileLabel: 'Đề thi',
    role: 'CONTENT_EDITOR',
    to: '/content/exams',
  },
  {
    activePrefixes: ['/admin/settings'],
    icon: Settings,
    label: 'Cài đặt hệ thống',
    mobileLabel: 'Cài đặt',
    role: 'ADMIN',
    to: '/admin/settings',
  },
  {
    activePrefixes: ['/content/topics/prerequisites'],
    icon: GitBranch,
    label: 'Tiên quyết chủ đề',
    mobileLabel: 'Tiên quyết',
    role: 'CONTENT_EDITOR',
    to: '/content/topics/prerequisites',
  },
  {
    activePrefixes: ['/content/contests'],
    icon: Trophy,
    label: 'Cuộc thi',
    role: 'CONTENT_EDITOR',
    to: '/content/contests',
  },
  {
    activePrefixes: ['/teacher/grading'],
    icon: PenTool,
    label: 'Chấm bài viết',
    role: 'TEACHER',
    to: '/teacher/grading',
  },
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
    activePrefixes: ['/admin/card-batches'],
    icon: CreditCard,
    label: 'Lô mã thẻ',
    mobileLabel: 'Lô thẻ',
    role: 'ADMIN',
    to: '/admin/card-batches',
  },
  {
    activePrefixes: ['/admin/plans'],
    icon: Layers,
    label: 'Gói dịch vụ',
    mobileLabel: 'Gói',
    role: 'ADMIN',
    to: '/admin/plans',
  },
  {
    activePrefixes: ['/admin/ledger'],
    icon: ReceiptText,
    label: 'Sổ cái điểm',
    mobileLabel: 'Sổ cái',
    role: 'ADMIN',
    to: '/admin/ledger',
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
