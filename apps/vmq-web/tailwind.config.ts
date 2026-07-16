import type { Config } from "tailwindcss"

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        border: "hsl(var(--border))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        muted: "hsl(var(--muted))",
        subtle: "hsl(var(--subtle))",
        primary: "hsl(var(--primary))",
        positive: "hsl(var(--positive))",
        warning: "hsl(var(--warning))",
        danger: "hsl(var(--danger))",
        surface: "hsl(var(--surface))",
        "surface-2": "hsl(var(--surface-2))",
      },
      borderRadius: {
        md: "8px",
      },
    },
  },
  plugins: [],
}

export default config
