"use client"

import { useState, useEffect } from "react"
import { useI18n } from "@/lib/i18n"
import { vmqApi, PayOrder } from "@/lib/api"
import { toast } from "sonner"
import { RefreshCw, Trash2, FileText, Send, X, ArrowLeft, ArrowRight } from "lucide-react"

export default function OrdersPage() {
  const { t } = useI18n()
  const [loading, setLoading] = useState(true)
  const [orders, setOrders] = useState<PayOrder[]>([])
  const [count, setCount] = useState(0)
  
  // Filters
  const [page, setPage] = useState(1)
  const [limit] = useState(20)
  const [type, setType] = useState<number | "">("")
  const [state, setState] = useState<number | "">("")
  
  // Detail Drawer
  const [detailOrder, setDetailOrder] = useState<PayOrder | null>(null)

  async function loadOrders() {
    setLoading(true)
    try {
      const res = await vmqApi.getOrders({
        page,
        limit,
        type: type === "" ? undefined : type,
        state: state === "" ? undefined : state,
      })
      if (res.code === 0 && res.data) {
        setOrders(res.data)
        setCount(res.count)
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
    loadOrders()
  }, [page, type, state])

  async function handleCallback(id: number) {
    if (!window.confirm(t("resendCallbackConfirm"))) return
    try {
      const res = await vmqApi.setBd(id)
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadOrders()
      } else {
        toast.error(res.msg || t("callbackFailed"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("callbackFailed"))
    }
  }

  async function handleDelete(id: number) {
    if (!window.confirm(t("deleteOrderConfirm"))) return
    try {
      const res = await vmqApi.deleteOrder(id)
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadOrders()
      } else {
        toast.error(res.msg || t("delete"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("delete"))
    }
  }

  async function handleDeleteExpired() {
    if (!window.confirm(t("deleteExpiredConfirm"))) return
    try {
      const res = await vmqApi.deleteExpiredOrders()
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadOrders()
      } else {
        toast.error(res.msg)
      }
    } catch (err: any) {
      toast.error(err?.message)
    }
  }

  async function handleDeleteOld() {
    if (!window.confirm(t("deleteOldConfirm"))) return
    try {
      const res = await vmqApi.deleteOldOrders()
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadOrders()
      } else {
        toast.error(res.msg)
      }
    } catch (err: any) {
      toast.error(err?.message)
    }
  }

  function formatTime(timestamp?: number) {
    if (!timestamp) return "-"
    return new Date(timestamp).toLocaleString("zh-CN", { hour12: false })
  }

  const totalPages = Math.ceil(count / limit) || 1

  return (
    <div className="space-y-6">
      {/* Title & Batch Actions */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{t("orders")}</h2>
          <p className="text-sm text-subtle mt-1">{t("ordersSubtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={loadOrders}
            className="flex items-center gap-1.5 px-4 py-2 border border-border bg-background/50 rounded-xl text-sm font-semibold hover:bg-muted transition"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
            <span>{t("refresh")}</span>
          </button>
          <button
            onClick={handleDeleteExpired}
            className="px-4 py-2 bg-warning text-background font-bold rounded-xl text-sm hover:bg-warning/90 transition"
          >
            {t("deleteExpired")}
          </button>
          <button
            onClick={handleDeleteOld}
            className="px-4 py-2 bg-danger text-background font-bold rounded-xl text-sm hover:bg-danger/90 transition"
          >
            {t("deleteOld")}
          </button>
        </div>
      </div>

      {/* Filters & Content Area */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* Left/Sidebar Filters */}
        <div className="w-full lg:w-64 space-y-4 shrink-0">
          <div className="glass-panel p-5 rounded-2xl space-y-4">
            <div>
              <label className="block text-xs font-bold text-subtle uppercase tracking-wider mb-2">{t("payType")}</label>
              <select
                value={type}
                onChange={(e) => {
                  setType(e.target.value === "" ? "" : Number(e.target.value))
                  setPage(1)
                }}
                className="w-full px-3 py-2 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground"
              >
                <option value="">{t("refresh")}</option>
                <option value={1}>{t("wechat")}</option>
                <option value={2}>{t("alipay")}</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold text-subtle uppercase tracking-wider mb-2">{t("state")}</label>
              <select
                value={state}
                onChange={(e) => {
                  setState(e.target.value === "" ? "" : Number(e.target.value))
                  setPage(1)
                }}
                className="w-full px-3 py-2 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground"
              >
                <option value="">{t("refresh")}</option>
                <option value={0}>{t("pending")}</option>
                <option value={1}>{t("success")}</option>
                <option value={2}>{t("callbackFailed")}</option>
                <option value={-1}>{t("closed")}</option>
              </select>
            </div>
          </div>
        </div>

        {/* Orders Table Container */}
        <div className="flex-1 min-w-0">
          <div className="glass-panel rounded-2xl overflow-hidden overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-border/50 bg-background/30 text-xs font-bold text-subtle uppercase tracking-wider">
                  <th className="p-4">{t("orderId")}</th>
                  <th className="p-4">{t("merchantId")}</th>
                  <th className="p-4">{t("payType")}</th>
                  <th className="p-4">{t("amount")}</th>
                  <th className="p-4">{t("realAmount")}</th>
                  <th className="p-4">{t("state")}</th>
                  <th className="p-4">{t("created")}</th>
                  <th className="p-4 text-right">{t("actions")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800 text-sm">
                {loading ? (
                  <tr>
                    <td colSpan={8} className="p-8 text-center text-foreground font-medium">
                      {t("loadingOrder")}
                    </td>
                  </tr>
                ) : orders.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="p-8 text-center text-subtle">
                      {t("orderNotFound")}
                    </td>
                  </tr>
                ) : (
                  orders.map((ord) => (
                    <tr key={ord.id} className="hover:bg-muted/30 transition">
                      <td className="p-4 font-mono text-xs select-all text-foreground">{ord.orderId}</td>
                      <td className="p-4 font-mono text-xs text-subtle">{ord.orderId}</td>
                      <td className="p-4">
                        <span className={`px-2.5 py-1 text-xs font-bold rounded-lg ${ord.type === 1 ? "bg-positive/10 text-positive" : "bg-primary/10 text-primary"}`}>
                          {ord.type === 1 ? t("wechat") : t("alipay")}
                        </span>
                      </td>
                      <td className="p-4 font-semibold text-foreground">￥{ord.price.toFixed(2)}</td>
                      <td className="p-4 font-semibold text-primary">￥{ord.reallyPrice.toFixed(2)}</td>
                      <td className="p-4">
                        <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                          ord.state === -1 ? "bg-muted text-subtle" :
                          ord.state === 0 ? "bg-warning/10 text-warning" :
                          ord.state === 1 ? "bg-positive/10 text-positive" : "bg-danger/10 text-danger"
                        }`}>
                          {ord.state === -1 ? t("closed") :
                           ord.state === 0 ? t("pending") :
                           ord.state === 1 ? t("success") : t("callbackFailed")}
                        </span>
                      </td>
                      <td className="p-4 text-xs text-subtle">{formatTime(ord.createDate)}</td>
                      <td className="p-4 text-right space-x-1.5 whitespace-nowrap">
                        <button
                          onClick={() => setDetailOrder(ord)}
                          className="p-1.5 rounded-lg text-subtle hover:text-foreground hover:bg-muted transition"
                          title={t("detail")}
                        >
                          <FileText className="w-4.5 h-4.5" />
                        </button>
                        <button
                          onClick={() => handleCallback(ord.id)}
                          className="p-1.5 rounded-lg text-primary hover:bg-primary/10 transition"
                          title={t("callback")}
                        >
                          <Send className="w-4.5 h-4.5" />
                        </button>
                        <button
                          onClick={() => handleDelete(ord.id)}
                          className="p-1.5 rounded-lg text-danger hover:bg-danger/10 transition"
                          title={t("delete")}
                        >
                          <Trash2 className="w-4.5 h-4.5" />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <span className="text-xs text-subtle">
                Total {count} items | Page {page} of {totalPages}
              </span>
              <div className="flex gap-2">
                <button
                  disabled={page <= 1}
                  onClick={() => setPage(p => Math.max(1, p - 1))}
                  className="p-2 border border-border rounded-xl text-subtle hover:text-foreground hover:bg-muted disabled:opacity-30 transition"
                >
                  <ArrowLeft className="w-4.5 h-4.5" />
                </button>
                <button
                  disabled={page >= totalPages}
                  onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                  className="p-2 border border-border rounded-xl text-subtle hover:text-foreground hover:bg-muted disabled:opacity-30 transition"
                >
                  <ArrowRight className="w-4.5 h-4.5" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Detail Drawer / Modal Overlay */}
      {detailOrder && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="glass-panel w-full max-w-lg p-6 rounded-2xl relative shadow-2xl space-y-6">
            <button
              onClick={() => setDetailOrder(null)}
              className="absolute top-4 right-4 p-1.5 rounded-lg text-subtle hover:text-foreground hover:bg-muted transition"
            >
              <X className="w-5 h-5" />
            </button>
            <div>
              <h3 className="text-lg font-bold text-foreground">{t("orderDetail")}</h3>
              <p className="text-xs text-subtle">Core information ledger</p>
            </div>
            
            <div className="space-y-3.5 text-sm">
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("orderId")}</span>
                <span className="font-mono text-foreground font-semibold">{detailOrder.orderId}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("merchantId")}</span>
                <span className="font-mono text-foreground font-semibold">{detailOrder.orderId}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("param")}</span>
                <span className="text-foreground">{detailOrder.param || "-"}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("notifyUrl")}</span>
                <span className="text-foreground text-xs break-all max-w-[70%] text-right">{detailOrder.notifyUrl || "-"}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("returnUrl")}</span>
                <span className="text-foreground text-xs break-all max-w-[70%] text-right">{detailOrder.returnUrl || "-"}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("payUrl")}</span>
                <span className="text-foreground text-xs break-all max-w-[70%] text-right">{detailOrder.payUrl || "-"}</span>
              </div>
              <div className="flex justify-between border-b border-border/50 pb-2">
                <span className="text-subtle">{t("payTime")}</span>
                <span className="text-foreground">{formatTime(detailOrder.payDate)}</span>
              </div>
              <div className="flex justify-between pb-2">
                <span className="text-subtle">{t("closeTime")}</span>
                <span className="text-foreground">{formatTime(detailOrder.closeDate)}</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
