"use client"

import { useEffect, useState } from "react"
import { useRouter, usePathname } from "next/navigation"
import Link from "next/link"
import { useI18n } from "@/lib/i18n"
import { useTheme } from "@/lib/theme"
import { vmqApi } from "@/lib/api"
import { LayoutDashboard, Receipt, Radio, Settings, LogOut, Moon, Sun, Languages, QrCode } from "lucide-react"

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { language, t, setLanguage } = useI18n()
  const { theme, toggleTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
    const token = localStorage.getItem("csrfToken")
    if (!token) {
      router.replace("/login")
    }
  }, [router])

  async function handleLogout() {
    try {
      await vmqApi.logout()
    } finally {
      localStorage.removeItem("csrfToken")
      router.replace("/login")
    }
  }

  if (!mounted) return null

  const menuItems = [
    { name: t("dashboard"), path: "/dashboard", icon: LayoutDashboard },
    { name: t("orders"), path: "/orders", icon: Receipt },
    { name: t("wechatQr"), path: "/qrcodes/wechat", icon: QrCode },
    { name: t("alipayQr"), path: "/qrcodes/alipay", icon: QrCode },
    { name: t("monitor"), path: "/monitor", icon: Radio },
    { name: t("settings"), path: "/settings", icon: Settings },
  ]

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top Header */}
      <header className="glass-panel border-b border-border/50 px-6 py-4 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-primary flex items-center justify-center font-bold text-background text-lg shadow-lg shadow-primary/20">
            V
          </div>
          <div>
            <h1 className="text-lg font-bold text-foreground leading-none">{t("appName")}</h1>
            <span className="text-xs text-subtle font-medium">{t("brandSubtitle")}</span>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <button
            onClick={() => setLanguage(language === "zh" ? "en" : "zh")}
            className="p-2 rounded-xl text-subtle hover:text-foreground hover:bg-muted transition duration-200"
            title={t("language")}
          >
            <Languages className="w-5 h-5" />
          </button>
          <button
            onClick={toggleTheme}
            className="p-2 rounded-xl text-subtle hover:text-foreground hover:bg-muted transition duration-200"
            title={t("theme")}
          >
            {theme === "dark" ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
          </button>
          <button
            onClick={handleLogout}
            className="p-2 rounded-xl text-danger hover:bg-danger/10 transition duration-200 ml-2"
            title={t("logout")}
          >
            <LogOut className="w-5 h-5" />
          </button>
        </div>
      </header>

      {/* Main Workspace */}
      <div className="flex-1 flex flex-col md:flex-row">
        {/* Sidebar Nav */}
        <aside className="w-full md:w-64 glass-panel border-r border-border/50 md:sticky md:top-20 md:h-[calc(100vh-5rem)] flex flex-col p-4 gap-2">
          <div className="text-xs font-bold text-subtle px-3 py-2 uppercase tracking-wider hidden md:block">
            {t("adminRole")}
          </div>
          <nav className="flex flex-row md:flex-col gap-1 overflow-x-auto md:overflow-x-visible pb-2 md:pb-0">
            {menuItems.map((item) => {
              const Icon = item.icon
              const active = pathname === item.path
              return (
                <Link
                  key={item.path}
                  href={item.path}
                  className={`flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-semibold transition duration-200 whitespace-nowrap ${
                    active
                      ? "bg-primary text-background shadow-md shadow-primary/10"
                      : "text-subtle hover:text-foreground hover:bg-muted"
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.name}</span>
                </Link>
              )
            })}
          </nav>
        </aside>

        {/* Workspace Content */}
        <main className="flex-1 p-6 md:p-8 max-w-7xl mx-auto w-full overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  )
}
