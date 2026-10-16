import { invoke } from "@tauri-apps/api/core"
import { VpnServer } from "@/types/vpn"
import { ServerListSchema } from "@/schemas/server"
export { DEFAULT_SERVERS } from "./servers"

export const VpnService = {
    async pingHost(host: string, port: number): Promise<number | null> {
        try {
            const latency = await invoke<number>("ping_tcp", { host, port })
            return latency
        } catch {
            return null
        }
    },

    async pingAll(servers: VpnServer[]): Promise<VpnServer[]> {
        const pingPromises = servers.map(async (server) => {
            const latency = await this.pingHost(server.host, server.port)
            return {
                ...server,
                pingMs: latency,
                isOnline: latency !== null
            }
        })

        const updated = await Promise.all(pingPromises)
        // Ordena servidores com menor ping primeiro
        return updated.sort((a, b) => {
            if (a.pingMs === null) return 1
            if (b.pingMs === null) return -1
            return a.pingMs - b.pingMs
        })
    },

    async fetchSubscriptionList(url: string): Promise<VpnServer[]> {
        const response = await fetch(url)
        if (!response.ok) {
            throw new Error(`Falha ao obter lista remota: ${response.statusText}`)
        }

        const rawText = await response.text()

        // 1. Tenta fazer parse como JSON se for um endpoint REST
        try {
            const json = JSON.parse(rawText)
            const parsed = ServerListSchema.safeParse(json)
            if (parsed.success) {
                return parsed.data.map((s) => ({
                    ...s,
                    pingMs: null,
                    isOnline: true
                }))
            }
        } catch {
            // Não é JSON, continua para parser de subscription Base64 / URI
        }

        // 2. Parser de URLs Base64 (VLESS / VMESS / TROJAN)
        let decoded = rawText
        try {
            decoded = atob(rawText.trim())
        } catch {
            decoded = rawText
        }

        const lines = decoded.split(/\r?\n/).filter((line) => line.trim().length > 0)
        const parsedServers: VpnServer[] = []

        lines.forEach((line, index) => {
            const trimmed = line.trim()
            if (trimmed.startsWith("vless://")) {
                const urlObj = new URL(trimmed)
                const name = decodeURIComponent(urlObj.hash.replace("#", "")) || `Servidor #${index + 1}`
                parsedServers.push({
                    id: `sub-${index}-${Date.now()}`,
                    name,
                    host: urlObj.hostname,
                    port: parseInt(urlObj.port) || 443,
                    protocol: "vless",
                    uuid: urlObj.username,
                    tls: urlObj.searchParams.get("security") === "tls",
                    pingMs: null,
                    isOnline: true,
                    countryCode: "UN",
                    flag: "🌐"
                })
            }
        })

        if (parsedServers.length === 0) {
            throw new Error("Nenhum servidor compatível (VLESS/JSON) foi encontrado na lista.")
        }

        return parsedServers
    },

    async connectTunnel(server: VpnServer): Promise<boolean> {
        return await invoke<boolean>("start_vpn_tunnel", {
            serverConfig: {
                host: server.host,
                port: server.port,
                protocol: server.protocol,
                uuid: server.uuid
            }
        })
    },

    async disconnectTunnel(): Promise<boolean> {
        return await invoke<boolean>("stop_vpn_tunnel")
    },

    async getVpnStatus(): Promise<{ isRunning: boolean; bytesUp: number; bytesDown: number }> {
        try {
            const res = await invoke<{ is_running: boolean; bytes_up: number; bytes_down: number }>("get_vpn_status")
            return {
                isRunning: res.is_running,
                bytesUp: res.bytes_up,
                bytesDown: res.bytes_down
            }
        } catch {
            return { isRunning: false, bytesUp: 0, bytesDown: 0 }
        }
    }
}
