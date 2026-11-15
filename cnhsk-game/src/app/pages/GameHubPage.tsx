export default function GameHubPage() {
  return (
    <section aria-labelledby="hub-title" className="space-y-8">
      <div className="relative overflow-hidden rounded-lg bg-brand-800 px-6 py-10 text-surface sm:px-10 sm:py-14">
        <p className="mb-3 text-label uppercase tracking-widest text-brand-100">
          Học bằng trải nghiệm
        </p>
        <h1 id="hub-title" className="max-w-reading text-h1-sm font-bold sm:text-display-sm">
          Luyện tiếng Trung qua trò chơi
        </h1>
        <p className="mt-5 max-w-reading text-body text-brand-50">
          Dành một chút thời gian cho chữ Hán, từ vựng và pinyin — từng lượt một.
        </p>
      </div>
      <div className="grid min-w-0 gap-4 sm:grid-cols-3">
        <article className="min-w-0 rounded-md border border-line bg-surface p-6">
          <span aria-hidden="true" className="font-hanzi text-hanzi-md text-brand-800">
            字
          </span>
          <h2 className="mt-3 text-h3">Nhớ mặt chữ</h2>
          <p className="mt-2 text-ink-secondary">Làm quen với hình dáng và cấu tạo của chữ Hán.</p>
        </article>
        <article className="min-w-0 rounded-md border border-line bg-surface p-6">
          <span aria-hidden="true" className="text-hanzi-md text-brand-800">
            ā
          </span>
          <h2 className="mt-3 text-h3">Gắn âm với nghĩa</h2>
          <p className="mt-2 text-ink-secondary">
            Kết nối pinyin, từ và ý nghĩa trong mỗi lượt luyện.
          </p>
        </article>
        <article className="min-w-0 rounded-md border border-line bg-surface p-6">
          <span aria-hidden="true" className="font-hanzi text-hanzi-md text-brand-800">
            习
          </span>
          <h2 className="mt-3 text-h3">Luyện đều mỗi ngày</h2>
          <p className="mt-2 text-ink-secondary">
            Tạo nhịp học nhẹ nhàng và tập trung vào từng bước nhỏ.
          </p>
        </article>
      </div>
    </section>
  )
}
