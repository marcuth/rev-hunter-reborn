export type VpnProtocol = "vless" | "vmess" | "trojan" | "socks5"

export type VpnConnectionStatus = "disconnected" | "connecting" | "connected" | "disconnecting"

export interface VpnServer {
    id: string
    name: string
    host: string
    port: number
    protocol: VpnProtocol
    uuid?: string
    encryption?: string
    flow?: string
    tls?: boolean
    pingMs: number | null
    isOnline: boolean
    countryCode?: string
    flag?: string
}

export interface VpnStats {
    uploadSpeed: string
    downloadSpeed: string
    totalUpload: string
    totalDownload: string
    durationSeconds: number
    currentPingMs: number | null
}

export interface SubscriptionSource {
    id: string
    name: string
    url: string
    lastUpdated?: string
    serverCount: number
}
