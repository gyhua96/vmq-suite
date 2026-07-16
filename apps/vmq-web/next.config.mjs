const basePath = process.env.NEXT_PUBLIC_VMQ_BASE_PATH || "/vmq"
const apiProxyUrl = process.env.VMQ_API_PROXY_URL || "http://localhost:50126"

/** @type {import('next').NextConfig} */
const nextConfig = {
  basePath,
  output: "standalone",
  async rewrites() {
    return [
      {
        source: '/vmq-api/:path*',
        destination: `${apiProxyUrl}/:path*`,
      },
    ]
  }
}

export default nextConfig
