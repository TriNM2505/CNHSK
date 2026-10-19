/**
 * CNHSK design tokens from docs/reference/design.md section 6.
 * Responsive typography is mobile-first: use `text-h1-sm sm:text-h1` and
 * `text-display-sm sm:text-display`.
 *
 * @type {import('tailwindcss').Config}
 */
module.exports = {
  theme: {
    screens: {
      sm: '640px',
      lg: '1024px',
      xl: '1440px',
    },
    extend: {
      colors: {
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
        surface: {
          DEFAULT: '#FFFFFF',
          subtle: '#FFF8F8',
        },
        ink: {
          DEFAULT: '#2F3033',
          secondary: '#62646A',
          muted: '#858890',
        },
        line: '#E3E4E8',
        success: {
          DEFAULT: '#237A4B',
          bg: '#EAF6EF',
        },
        warning: {
          DEFAULT: '#9A6200',
          bg: '#FFF5D9',
        },
        danger: {
          DEFAULT: '#C0262D',
          bg: '#FDECEE',
        },
        info: {
          DEFAULT: '#246B9E',
          bg: '#EAF4FB',
        },
        focus: '#2563EB',
      },
      fontFamily: {
        sans: ['Be Vietnam Pro', 'system-ui', 'sans-serif'],
        hanzi: ['Noto Serif SC', 'Songti SC', 'serif'],
      },
      fontSize: {
        display: ['48px', { lineHeight: '56px', fontWeight: '700' }],
        'display-sm': ['36px', { lineHeight: '44px', fontWeight: '700' }],
        h1: ['32px', { lineHeight: '40px', fontWeight: '700' }],
        'h1-sm': ['28px', { lineHeight: '36px', fontWeight: '700' }],
        h2: ['24px', { lineHeight: '32px', fontWeight: '700' }],
        h3: ['20px', { lineHeight: '28px', fontWeight: '600' }],
        body: ['16px', { lineHeight: '24px', fontWeight: '400' }],
        'body-sm': ['14px', { lineHeight: '20px', fontWeight: '400' }],
        label: ['14px', { lineHeight: '20px', fontWeight: '600' }],
        caption: ['12px', { lineHeight: '16px', fontWeight: '500' }],
        'hanzi-lg': ['56px', { lineHeight: '68px', fontWeight: '500' }],
        'hanzi-md': ['40px', { lineHeight: '52px', fontWeight: '500' }],
      },
      borderRadius: {
        sm: '8px',
        md: '12px',
        lg: '20px',
        pill: '999px',
      },
      boxShadow: {
        sm: '0 1px 2px rgba(31, 35, 40, 0.08)',
        md: '0 8px 24px rgba(31, 35, 40, 0.10)',
        overlay: '0 20px 48px rgba(31, 35, 40, 0.18)',
      },
      maxWidth: {
        content: '1280px',
        reading: '720px',
      },
      height: {
        control: '44px',
        touch: '44px',
      },
      minHeight: {
        control: '44px',
        touch: '44px',
      },
      zIndex: {
        base: '0',
        nav: '20',
        dropdown: '40',
        overlay: '60',
        toast: '80',
      },
    },
  },
}
