import { Button } from '@/shared/ui'

export function NotFoundPage() {
  return (
    <main className="flex min-h-[calc(100vh-4rem)] items-center justify-center bg-canvas p-4">
      <div className="max-w-reading text-center">
        <p className="text-label text-brand-600">404</p>
        <h1 className="mt-2 text-h1-sm text-ink sm:text-h1">Không tìm thấy trang</h1>
        <p className="mt-3 text-body text-ink-secondary">
          Địa chỉ bạn truy cập không tồn tại hoặc đã được thay đổi.
        </p>
        <Button className="mt-6" to="/">
          Về trang chủ
        </Button>
      </div>
    </main>
  )
}
