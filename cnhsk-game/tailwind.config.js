/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // CNHSK design tokens (docs/reference/design.md §6)
        brand: {
          50: '#FFF4F4',
          100: '#FFDFDF',
          600: '#AF0000',
          700: '#8F0000',
          800: '#6E0000',
        },
        accent: {
          500: '#F3C650',
          700: '#8A6500',
        },
        canvas: '#FAFAF8',
        surface: '#FFFFFF',
        subtle: '#FFF8F8',
        ink: {
          DEFAULT: '#2F3033',
          soft: '#62646A',
          muted: '#858890',
        },
        line: '#E3E4E8',
        ok: { DEFAULT: '#237A4B', bg: '#EAF6EF' },
        warn: { DEFAULT: '#9A6200', bg: '#FFF5D9' },
        bad: { DEFAULT: '#C0262D', bg: '#FDECEE' },
        info: { DEFAULT: '#246B9E', bg: '#EAF4FB' },
        ring: '#2563EB',
        // Night scene palette (original CNHSK arcade art)
        night: {
          900: '#0B1020',
          800: '#131B33',
          700: '#1C2748',
          600: '#2A375C',
          500: '#3D4C77',
        },
        snow: '#F2F6FF',
        jade: '#4FB286',
        lantern: '#E2483F',
      },
      fontFamily: {
        sans: ['"Be Vietnam Pro"', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        hanzi: ['"Noto Serif SC"', '"Songti SC"', 'SimSun', '"Microsoft YaHei"', 'serif'],
      },
      borderRadius: {
        xl2: '1.25rem',
      },
      boxShadow: {
        card: '0 1px 2px rgba(47,48,51,.04), 0 8px 24px -12px rgba(47,48,51,.18)',
        pop: '0 12px 40px -12px rgba(47,48,51,.35)',
        glow: '0 0 0 1px rgba(255,255,255,.08), 0 18px 50px -20px rgba(0,0,0,.6)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'pop-in': {
          '0%': { opacity: '0', transform: 'scale(.92)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        shake: {
          '0%,100%': { transform: 'translateX(0)' },
          '20%': { transform: 'translateX(-7px)' },
          '40%': { transform: 'translateX(7px)' },
          '60%': { transform: 'translateX(-4px)' },
          '80%': { transform: 'translateX(4px)' },
        },
        'pulse-ring': {
          '0%': { opacity: '.7', transform: 'scale(.85)' },
          '100%': { opacity: '0', transform: 'scale(1.35)' },
        },
        floaty: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' },
        },
      },
      animation: {
        'fade-up': 'fade-up .35s ease-out both',
        'pop-in': 'pop-in .28s cubic-bezier(.2,.9,.3,1.3) both',
        shake: 'shake .38s ease-in-out',
        'pulse-ring': 'pulse-ring .6s ease-out forwards',
        floaty: 'floaty 3.6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
