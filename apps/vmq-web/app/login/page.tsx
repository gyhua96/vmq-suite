"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useI18n } from "@/lib/i18n"
import { useTheme } from "@/lib/theme"
import { vmqApi } from "@/lib/api"
import { toast } from "sonner"

export default function LoginPage() {
  const router = useRouter()
  const { language, t, setLanguage } = useI18n()
  const { theme, toggleTheme } = useTheme()
  const [user, setUser] = useState("")
  const [pass, setPass] = useState("")
  const [loading, setLoading] = useState(false)

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    if (loading) return
    if (!user.trim() || !pass) {
      toast.error(t("loginFailed"))
      return
    }
    setLoading(true)
    try {
      const res = await vmqApi.login(user.trim(), pass)
      if (res.code === 1 && res.data?.csrfToken) {
        toast.success(res.msg || t("success"))
        localStorage.setItem("csrfToken", res.data.csrfToken)
        router.replace("/dashboard")
      } else {
        toast.error(res.msg || t("loginFailed"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("loginFailed"))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-height-screen items-center justify-center p-4 min-h-screen">
      <div className="glass-panel w-full max-w-md p-8 rounded-2xl shadow-2xl relative overflow-hidden tech-card">
        <div className="flex justify-between items-start mb-8 relative z-10">
          <div>
            <h2 className="text-2xl font-bold tracking-tight text-foreground">{t("loginTitle")}</h2>
            <div className="text-sm text-subtle mt-1">{t("loginSubtitle")}</div>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setLanguage(language === "zh" ? "en" : "zh")}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold text-foreground hover:bg-muted transition duration-200"
            >
              {language === "zh" ? "EN" : "ZH"}
            </button>
            <button
              onClick={toggleTheme}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold text-foreground hover:bg-muted transition duration-200"
            >
              {theme === "dark" ? t("light") : t("dark")}
            </button>
          </div>
        </div>

        <form onSubmit={handleLogin} className="space-y-6 relative z-10">
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("user")}</label>
            <input
              type="text"
              value={user}
              onChange={(e) => setUser(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
              autoComplete="username"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("password")}</label>
            <input
              type="password"
              value={pass}
              onChange={(e) => setPass(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
              autoComplete="current-password"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-primary text-background font-bold rounded-xl hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary/50 transition disabled:opacity-50"
          >
            {loading ? t("loadingOrder") : t("login")}
          </button>
        </form>

        <div className="flex items-center gap-3 mt-6 p-4 border border-border/50 rounded-xl bg-primary/5 text-xs text-subtle relative z-10">
          <span className="w-2.5 h-2.5 rounded-full bg-primary animate-pulse"></span>
          <span>{t("sessionHint")}</span>
        </div>
      </div>
    </div>
  )
}
