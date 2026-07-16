"use client"

import { createContext, useContext, useEffect, useState } from "react"

type Theme = "light" | "dark"

const ThemeContext = createContext<{
  theme: Theme
  toggleTheme: () => void
}>({
  theme: "dark",
  toggleTheme: () => {},
})

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>("dark")

  useEffect(() => {
    const saved = localStorage.getItem("vmq.theme") as Theme
    if (saved === "light" || saved === "dark") {
      setThemeState(saved)
      document.documentElement.dataset.theme = saved
    } else {
      document.documentElement.dataset.theme = "dark"
    }
  }, [])

  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark"
    setThemeState(next)
    localStorage.setItem("vmq.theme", next)
    document.documentElement.dataset.theme = next
  }

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  return useContext(ThemeContext)
}
