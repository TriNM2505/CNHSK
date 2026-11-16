import { cp, readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { basename, dirname, join, resolve } from 'node:path'
import { defineConfig, type Connect, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

const strokePrefix = '/stroke-data/'
const licenseFile = 'ARPHICPL.TXT'
const characterFile = /^\p{Script=Han}\.json$/u
const require = createRequire(import.meta.url)

function strokeDataMiddleware(directory: string): Connect.NextHandleFunction {
  return (request, response, next) => {
    const pathname = request.url?.split('?', 1)[0] ?? ''
    if (!pathname.startsWith(strokePrefix)) return next()

    const notFound = () => {
      response.statusCode = 404
      response.setHeader('content-type', 'text/plain; charset=utf-8')
      response.end('Not found')
    }

    let file: string
    try {
      file = decodeURIComponent(pathname.slice(strokePrefix.length))
    } catch {
      notFound()
      return
    }
    if (file !== licenseFile && !characterFile.test(file)) {
      notFound()
      return
    }

    void readFile(join(directory, file)).then((data) => {
      response.setHeader('content-type', file === licenseFile ? 'text/plain; charset=utf-8' : 'application/json')
      response.end(request.method === 'HEAD' ? undefined : data)
    }, notFound)
  }
}

function strokeDataAssets(): Plugin {
  const source = dirname(require.resolve('hanzi-writer-data/package.json'))
  return {
    name: 'cnhsk-stroke-data',
    configureServer(server) {
      server.middlewares.use(strokeDataMiddleware(source))
    },
    configurePreviewServer(server) {
      const dataDir = resolve(server.config.root, server.config.build.outDir, 'stroke-data')
      server.middlewares.use(strokeDataMiddleware(dataDir))
    },
    async writeBundle(output) {
      if (!output.dir) throw new Error('The game build must specify an output directory.')
      await cp(source, resolve(output.dir, 'stroke-data'), {
        recursive: true,
        filter: (path) => path === source || basename(path) === licenseFile
          || (path.endsWith('.json') && basename(path) !== 'package.json'),
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), strokeDataAssets()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    strictPort: true,
    proxy: { '/api': { target: 'http://localhost:8080' } },
  },
  preview: { host: '127.0.0.1', port: 5174, strictPort: true },
})
