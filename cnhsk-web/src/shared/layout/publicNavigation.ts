import { GraduationCap, LayoutGrid, Search, UsersRound } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'

export type PublicMenuLink = { label: string; to: string }

export type PublicMenu = {
  columns: readonly { links: readonly PublicMenuLink[]; title: string }[]
  icon: ComponentType<SVGProps<SVGSVGElement>>
  id: string
  label: string
  /** Shown under the columns when the links lead to screens behind sign-in. */
  note?: string
}

/** What a guest can open from the public header; anchors are the sections of the landing page. */
export const publicMenus: readonly PublicMenu[] = [
  {
    id: 'about',
    icon: LayoutGrid,
    label: 'Giới thiệu',
    columns: [
      {
        title: 'Về Học Bá',
        links: [
          { label: 'Học Bá Education', to: '/#gioi-thieu' },
          { label: 'Phương pháp đào tạo', to: '/#phuong-phap' },
          { label: 'Cam kết đầu ra', to: '/#cam-ket' },
          { label: 'Cố vấn chuyên môn', to: '/#co-van' },
        ],
      },
      {
        title: 'Khoá học',
        links: [
          { label: 'Học Bá HSK 3.0', to: '/#khoa-hoc' },
          { label: 'Học Bá Chinese', to: '/#khoa-hoc' },
        ],
      },
      {
        title: 'Nền tảng CNHSK',
        links: [{ label: 'Các cách tự luyện', to: '/#luyen-tap' }],
      },
    ],
  },
  {
    id: 'practice',
    icon: GraduationCap,
    label: 'Luyện tập',
    note: 'Cần đăng nhập. Chọn một mục, đăng nhập xong bạn vào thẳng mục đó.',
    columns: [
      {
        title: 'Học mỗi ngày',
        links: [
          { label: 'Từ vựng theo chủ đề', to: '/learn/topics' },
          { label: 'Ngữ pháp', to: '/learn/grammar' },
          { label: 'Phát âm', to: '/learn/pronunciation' },
          { label: 'Chữ Hán', to: '/learn/characters/recognition' },
        ],
      },
      {
        title: 'Luyện thi',
        links: [
          { label: 'Thi thử HSK', to: '/learn/exams' },
          { label: 'Quiz theo chủ đề', to: '/community/quizzes' },
        ],
      },
      {
        title: 'Hỗ trợ',
        links: [
          { label: 'Chấm bài viết', to: '/grading' },
          { label: 'Dịch câu, đoạn văn', to: '/translate' },
        ],
      },
    ],
  },
  {
    id: 'lookup',
    icon: Search,
    label: 'Tra cứu',
    columns: [
      {
        title: 'Từ điển Trung – Việt',
        links: [
          { label: 'Tra theo chữ Hán', to: '/dictionary?type=HANZI' },
          { label: 'Tra theo pinyin', to: '/dictionary?type=PINYIN' },
          { label: 'Tra theo nghĩa tiếng Việt', to: '/dictionary?type=MEANING' },
          { label: 'Tra theo bộ thủ, số nét', to: '/dictionary?type=RADICAL' },
        ],
      },
      {
        title: 'Tài liệu tham khảo',
        links: [
          { label: 'Video và podcast', to: '/references/media' },
          { label: 'Sách', to: '/references/books' },
        ],
      },
    ],
  },
  {
    id: 'community',
    icon: UsersRound,
    label: 'Cộng đồng',
    columns: [
      {
        title: 'Cùng học',
        links: [
          { label: 'Bài viết', to: '/community' },
          { label: 'Cuộc thi', to: '/contests' },
        ],
      },
      {
        title: 'Bảng xếp hạng',
        links: [
          { label: 'Theo chủ đề', to: '/leaderboards/topics' },
          { label: 'Theo trò chơi', to: '/leaderboards/games' },
        ],
      },
    ],
  },
]