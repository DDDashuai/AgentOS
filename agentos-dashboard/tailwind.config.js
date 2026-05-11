/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        surface: '#050508',
        panel: '#0a0a14',
        'panel-light': '#0f0f1e',
        border: '#1a1a2e',
        accent: '#00ff88',
        'accent-dim': '#00cc6a',
        cyan: '#00d4ff',
        'cyan-dim': '#00a8cc',
        warn: '#ffcc00',
        error: '#ff4444',
        text: '#f0f0f0',
        'text-dim': '#6b7280',
        'text-muted': '#3a3a4e',
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      keyframes: {
        'pulse-border': {
          '0%, 100%': { borderColor: '#00d4ff' },
          '50%': { borderColor: 'rgba(0, 212, 255, 0.4)' },
        },
      },
      animation: {
        'pulse-border': 'pulse-border 1.5s ease-in-out infinite',
        'slide-in': 'slideIn 0.3s ease-out',
      },
    },
  },
  plugins: [],
}
