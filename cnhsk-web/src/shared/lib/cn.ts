import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

const mergeClasses = extendTailwindMerge({
  extend: {
    theme: {
      text: [
        'display',
        'display-sm',
        'h1',
        'h1-sm',
        'h2',
        'h3',
        'body',
        'body-sm',
        'label',
        'caption',
        'hanzi-lg',
        'hanzi-md',
      ],
    },
  },
})

export function cn(...inputs: ClassValue[]) {
  return mergeClasses(clsx(inputs))
}
