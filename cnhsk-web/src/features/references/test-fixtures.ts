import type { PublicReference, PublicReferencePage } from './types'

// Original fixture descriptions, not copied from external resources.
export const mediaReference: PublicReference = {
  id: 501,
  kind: 'YOUTUBE_CHANNEL',
  title: 'Góc luyện nghe demo',
  description: 'Các đoạn hội thoại ngắn về sinh hoạt hằng ngày, dành cho người mới học.',
  url: 'https://youtube.com/@cnhsk-demo',
  thumbnailUrl: null,
  hskMin: 1,
  hskMax: 3,
  tags: ['nghe', 'hội thoại'],
}

export const podcastReference: PublicReference = {
  ...mediaReference,
  id: 502,
  kind: 'PODCAST',
  title: 'Chuyện nhỏ mỗi ngày demo',
  url: 'https://open.spotify.com/show/cnhsk-demo',
  hskMin: null,
  hskMax: null,
}

export const bookReference: PublicReference = {
  id: 503,
  kind: 'BOOK',
  title: 'Sổ tay hội thoại demo',
  description: 'Ví dụ tự soạn về hỏi đường và mua đồ.\nĐọc chậm, rồi thử nói lại bằng lời của bạn.',
  url: 'https://fahasa.com/cnhsk-demo',
  thumbnailUrl: null,
  hskMin: 1,
  hskMax: null,
  tags: ['đọc', 'hội thoại'],
  author: 'Nhóm biên soạn demo',
  publisher: null,
  publishedYear: null,
  isAffiliate: true,
}

export const mediaPage: PublicReferencePage = {
  items: [mediaReference],
  page: 0,
  size: 20,
  total: 1,
}
export const bookPage: PublicReferencePage = { items: [bookReference], page: 0, size: 20, total: 1 }
