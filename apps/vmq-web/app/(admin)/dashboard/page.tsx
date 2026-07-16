"use client"

import { useState, useEffect } from "react"
import { useI18n } from "@/lib/i18n"
import { vmqApi } from "@/lib/api"
import { toast } from "sonner"
import { Receipt, CheckCircle, XCircle, DollarSign, RefreshCw, Cpu, ShieldAlert, Sparkles } from "lucide-react"

export default function DashboardPage() {
  const { t } = useI18n()
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<Record<string, string>>({})

  async function loadStats() {
    setLoading(true)
    try {
      const res = await vmqApi.getMain()
      if (res.code === 1 && res.data) {
        setStats(res.data)
      } else {
        toast.error(res.msg || t("refresh"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("refresh"))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadStats()
  }, [])

  const cards = [
    {
      label: t("todayOrders"),
      value: stats.todayOrder ?? "0",
      trend: t("liveMetric"),
      icon: Receipt,
      color: "text-primary",
      bg: "bg-primary/10",
    },
    {
      label: t("todaySuccess"),
      value: stats.todaySuccessOrder ?? "0",
      trend: t("successMetric"),
      icon: CheckCircle,
      color: "text-positive",
      bg: "bg-positive/10",
    },
    {
      label: t("todayClosed"),
      value: stats.todayCloseOrder ?? "0",
      trend: t("closedMetric"),
      icon: XCircle,
      color: "text-danger",
      bg: "bg-danger/10",
    },
    {
      label: t("todayAmount"),
      value: stats.todayMoney ? `￥${Number(stats.todayMoney).toFixed(2)}` : "￥0.00",
      trend: t("amountMetric"),
      icon: DollarSign,
      color: "text-warning",
      bg: "bg-warning/10",
    },
  ]

  return (
    <div className="space-y-8">
      {/* Page Title & Refresh */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{t("dashboardHeroTitle")}</h2>
          <p className="text-sm text-subtle mt-1">{t("dashboardHeroDescription")}</p>
        </div>
        <button
          onClick={loadStats}
          disabled={loading}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-primary text-background font-bold rounded-xl hover:bg-primary/90 transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          <span>{t("refresh")}</span>
        </button>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {cards.map((card, idx) => {
          const Icon = card.icon
          return (
            <div key={card.label} className="glass-panel p-6 rounded-2xl relative overflow-hidden tech-card surface-hover">
              <div className="flex justify-between items-start mb-4">
                <div className={`p-3 rounded-xl ${card.bg} ${card.color}`}>
                  <Icon className="w-6 h-6" />
                </div>
                <span className="text-xs font-bold text-subtle opacity-50">0{idx + 1}</span>
              </div>
              <div className="text-xs font-bold text-subtle uppercase tracking-wider mb-1">{card.label}</div>
              <div className="text-3xl font-extrabold text-foreground tracking-tight mb-2">{card.value}</div>
              <div className="text-xs font-semibold text-primary">{card.trend}</div>
            </div>
          )
        })}
      </div>

      {/* Bottom Info Panels */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Transaction Pipeline */}
        <div className="glass-panel p-6 rounded-2xl lg:col-span-2">
          <h3 className="text-base font-bold text-foreground mb-4 flex items-center gap-2">
            <Cpu className="w-5 h-5 text-primary" />
            <span>{t("transactionPipeline")}</span>
          </h3>
          <div className="h-48 flex items-center justify-center border border-dashed border-border rounded-xl bg-background/30 text-sm text-subtle">
            {t("pipelineTitle")}
          </div>
        </div>

        {/* AI Copilot & Status */}
        <div className="glass-panel p-6 rounded-2xl flex flex-col justify-between space-y-6">
          <div>
            <h3 className="text-base font-bold text-foreground mb-4 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-warning" />
              <span>{t("assistantTitle")}</span>
            </h3>
            <div className="space-y-4">
              <div className="p-3 border border-border/50 rounded-xl bg-primary/5 text-xs text-foreground leading-relaxed">
                {t("assistantMessageOne")}
              </div>
              <div className="p-3 border border-border/50 rounded-xl bg-positive/5 text-xs text-foreground leading-relaxed">
                {t("assistantMessageTwo")}
              </div>
            </div>
          </div>
          
          <div className="flex items-center justify-between p-4 border border-border/50 rounded-xl bg-background/50 text-xs">
            <span className="text-subtle flex items-center gap-1.5">
              <ShieldAlert className="w-4 h-4 text-positive animate-pulse" />
              <span>{t("riskPosture")}</span>
            </span>
            <span className="font-bold text-positive uppercase">{t("stable")}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
