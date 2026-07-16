"use client"

import { useState, useEffect, useMemo } from "react"
import { useI18n } from "@/lib/i18n"
import { vmqApi, SettingsMap } from "@/lib/api"
import { toast } from "sonner"
import { RefreshCw, Radio, HardDrive, ShieldCheck } from "lucide-react"

export default function MonitorPage() {
  const { t } = useI18n()
  const [loading, setLoading] = useState(true)
  const [settings, setSettings] = useState<SettingsMap | null>(null)
  const [serverUrl, setServerUrl] = useState("")
  const [secretKey, setSecretKey] = useState("")

  async function loadData() {
    setLoading(true)
    try {
      const res = await vmqApi.getSettings()
      if (res.code === 1 && res.data) {
        setSettings(res.data)
        setSecretKey(res.data.key || "")
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
    if (typeof window !== "undefined") {
      setServerUrl(window.location.origin)
    }
    loadData()
  }, [])

  const qrConfigText = useMemo(() => {
    if (!serverUrl || !secretKey) return ""
    const cleanUrl = serverUrl.trim().replace(/^https?:\/\//i, "").replace(/\/+$/, "")
    return `${cleanUrl}/${secretKey.trim()}`
  }, [serverUrl, secretKey])

  const qrCodeSrc = useMemo(() => {
    if (!qrConfigText) return ""
    return vmqApi.encodeQrcodeUrl(qrConfigText)
  }, [qrConfigText])

  function formatTime(timestamp?: string) {
    if (!timestamp) return "-"
    const num = Number(timestamp)
    if (Number.isNaN(num) || num === 0) return "-"
    return new Date(num).toLocaleString("zh-CN", { hour12: false })
  }

  const isOnline = settings?.jkstate === "1"
  const isOffline = settings?.jkstate === "0"

  const stateText = isOnline ? t("online") : isOffline ? t("offline") : t("notInitialized")
  const stateOrbColor = isOnline ? "bg-positive shadow-positive/20 animate-pulse" : isOffline ? "bg-danger shadow-danger/20" : "bg-warning shadow-warning/20"
  const stateTextColor = isOnline ? "text-positive" : isOffline ? "text-danger" : "text-warning"

  return (
    <div className="space-y-6">
      {/* Title & Refresh */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{t("androidMonitor")}</h2>
          <p className="text-sm text-subtle mt-1">{t("monitorSubtitle")}</p>
        </div>
        <button
          onClick={loadData}
          disabled={loading}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-primary text-background font-bold rounded-xl hover:bg-primary/90 transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          <span>{t("refresh")}</span>
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Status Panels */}
        <div className="lg:col-span-2 space-y-6">
          {/* Main Status Orb */}
          <div className="glass-panel p-6 rounded-2xl flex flex-col md:flex-row items-center gap-8 relative overflow-hidden tech-card">
            <div className="flex items-center justify-center relative shrink-0">
              <div className={`w-28 h-28 rounded-full border-4 border-border/50 flex flex-col items-center justify-center shadow-2xl relative z-10 bg-background/50`}>
                <div className={`w-3.5 h-3.5 rounded-full absolute top-2 right-2 ${stateOrbColor} shadow-[0_0_12px_4px]`}></div>
                <Radio className={`w-8 h-8 text-subtle mb-1 ${isOnline ? "animate-bounce" : ""}`} />
                <span className="text-xs text-subtle font-semibold">{t("state")}</span>
                <span className={`text-sm font-bold ${stateTextColor}`}>{stateText}</span>
              </div>
            </div>
            
            <div className="space-y-3 text-center md:text-left">
              <div className="flex items-center justify-center md:justify-start gap-2">
                <span className={`w-2.5 h-2.5 rounded-full ${stateOrbColor}`}></span>
                <span className="text-xs font-bold text-subtle uppercase tracking-wider">{stateText}</span>
              </div>
              <h3 className="text-lg font-bold text-foreground">{t("androidMonitor")}</h3>
              <p className="text-sm text-subtle max-w-md">{t("monitorSubtitle")}</p>
            </div>
          </div>

          {/* Telemetry Metrics */}
          <div className="glass-panel p-6 rounded-2xl grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div className="p-4 border border-border/50 rounded-xl bg-background/30 space-y-2">
              <div className="text-xs font-bold text-subtle uppercase tracking-wider">{t("lastHeartbeat")}</div>
              <div className="text-base font-bold text-foreground">{formatTime(settings?.lastheart)}</div>
            </div>
            <div className="p-4 border border-border/50 rounded-xl bg-background/30 space-y-2">
              <div className="text-xs font-bold text-subtle uppercase tracking-wider">{t("lastPayment")}</div>
              <div className="text-base font-bold text-foreground">{formatTime(settings?.lastpay)}</div>
            </div>
          </div>
        </div>

        {/* Pairing Config Card */}
        <div className="glass-panel p-6 rounded-2xl space-y-6">
          <div>
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-primary" />
              <span>{t("appConfigQr")}</span>
            </h3>
            <p className="text-xs text-subtle mt-1">Scan with Android app to pair</p>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-foreground mb-2">{t("serverUrl")}</label>
              <input
                type="text"
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
                placeholder="https://example.com"
                className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-foreground mb-2">{t("secretKey")}</label>
              <input
                type="password"
                value={secretKey}
                onChange={(e) => setSecretKey(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
              />
            </div>
          </div>

          {qrCodeSrc ? (
            <div className="flex flex-col items-center gap-3 pt-4 border-t border-border/50">
              <div className="p-3 bg-white rounded-xl shadow-inner inline-block">
                <img src={qrCodeSrc} alt="app config QR code" className="w-44 h-44 object-contain" />
              </div>
              <div className="text-[10px] font-mono text-subtle break-all select-all text-center max-w-xs p-2 border border-border/50 rounded-lg bg-background/50">
                {qrConfigText}
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
