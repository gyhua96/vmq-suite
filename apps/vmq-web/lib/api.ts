export interface CommonRes<T> {
  code: number
  msg: string
  data: T
}

export interface PageRes<T> {
  code: number
  msg: string
  count: number
  data: T[]
}

export interface PayOrder {
  id: number
  orderId: string
  reallyPrice: number
  price: number
  type: number
  state: number
  createDate: number
  payDate?: number
  closeDate?: number
  param?: string
  notifyUrl?: string
  returnUrl?: string
  payUrl?: string
}

export interface PayQrcode {
  id: number
  price: number
  reallyPrice: number
  type: number
  payUrl: string
}

export type SettingsMap = Record<string, string>

export interface CreateOrderRes {
  orderId: string
  reallyPrice: number
  payUrl: string
  timeOut: number
  date: number
  state: number
  accessToken?: string
  accessExpiresAt?: number
}

const API_BASE = typeof window !== "undefined" ? (process.env.NEXT_PUBLIC_VMQ_API_BASE || "/vmq-api") : "/vmq-api"

function toFormData(data: Record<string, unknown>) {
  const params = new URLSearchParams()
  Object.entries(data).forEach(([key, value]) => {
    if (value === undefined || value === null) return
    params.set(key, String(value))
  })
  return params
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const csrfToken = typeof window !== "undefined" ? localStorage.getItem("csrfToken") : ""
  
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  }
  
  if (csrfToken) {
    headers["X-CSRF-Token"] = csrfToken
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (res.status === 401) {
    if (typeof window !== "undefined") {
      localStorage.removeItem("csrfToken")
      window.location.href = "/vmq/login"
      return new Promise(() => {})
    }
  }

  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`)
  }

  return res.json() as Promise<T>
}

export const vmqApi = {
  login: (user: string, pass: string) =>
    request<CommonRes<{ csrfToken: string }>>("/login", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ user, pass }),
    }),

  logout: () =>
    request<CommonRes<null>>("/logout", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({}),
    }),

  getMenu: () => request<CommonRes<unknown[]>>("/admin/getMenu"),
  
  getMain: () => request<CommonRes<Record<string, string>>>("/admin/getMain"),
  
  getSettings: () => request<CommonRes<SettingsMap>>("/admin/getSettings"),
  
  saveSetting: (payload: Record<string, unknown>) =>
    request<CommonRes<null>>("/admin/saveSetting", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData(payload),
    }),

  getOrders: (params: Record<string, unknown>) => {
    const qs = toFormData(params).toString()
    return request<PageRes<PayOrder>>(`/admin/getOrders?${qs}`)
  },

  setBd: (id: number) =>
    request<CommonRes<null>>("/admin/setBd", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ id }),
    }),

  getPayQrcodes: (params: Record<string, unknown>) => {
    const qs = toFormData(params).toString()
    return request<PageRes<PayQrcode>>(`/admin/getPayQrcodes?${qs}`)
  },

  addPayQrcode: (payload: Record<string, unknown>) =>
    request<CommonRes<null>>("/admin/addPayQrcode", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData(payload),
    }),

  updatePayQrcode: (payload: Record<string, unknown>) =>
    request<CommonRes<null>>("/admin/updatePayQrcode", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData(payload),
    }),

  deletePayQrcode: (id: number) =>
    request<CommonRes<null>>("/admin/delPayQrcode", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ id }),
    }),

  deleteOrder: (id: number) =>
    request<CommonRes<null>>("/admin/delOrder", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ id }),
    }),

  deleteExpiredOrders: () =>
    request<CommonRes<null>>("/admin/delGqOrder", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({}),
    }),

  deleteOldOrders: () =>
    request<CommonRes<null>>("/admin/delLastOrder", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({}),
    }),

  decodeQrcodeFile: (file: File) => {
    const form = new FormData()
    form.append("file", file)
    return request<CommonRes<string>>("/deQrcode2", {
      method: "POST",
      body: form,
    })
  },

  getOrder: (orderId: string, accessToken?: string) =>
    request<CommonRes<CreateOrderRes>>("/getOrder", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ orderId, accessToken }),
    }),

  checkOrder: (orderId: string, accessToken?: string) =>
    request<CommonRes<string>>("/checkOrder", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ orderId, accessToken }),
    }),

  getState: (t: string) =>
    request<CommonRes<{ state: string; lastheart: string; lastpay: string }>>("/getState", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ t }),
    }),

  decodeQrcodeBase64: (base64: string) =>
    request<CommonRes<string>>("/deQrcode", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: toFormData({ base64 }),
    }),

  encodeQrcodeUrl: (url: string) =>
    `${API_BASE}/enQrcode?url=${encodeURIComponent(url)}`,
}
