/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        dark: {
          950: '#070a13',
          900: '#0b101e',
          800: '#131b2e',
          700: '#1f2b48',
          600: '#2d3b5e'
        }
      }
    },
  },
  plugins: [],
}
