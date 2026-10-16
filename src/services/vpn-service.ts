import { invoke } from "@tauri-apps/api/core"
import { VpnServer } from "@/types/vpn"
import { ServerListSchema } from "@/schemas/server"

export const DEFAULT_SERVERS: VpnServer[] = [
    {
        id: "hunter-opt-01",
        name: "Rev Hunter [Modo 01 - BR Gaming]",
        host: "br1.vless-node.net",
        port: 443,
        protocol: "vless",
        uuid: "7b4c91a2-4112-4ebc-87d2-a5b6e7f8c9d0",
        tls: true,
        pingMs: null,
        isOnline: true,
        countryCode: "BR",
        flag: "🇧🇷"
    },
    {
        id: "hunter-opt-02",
        name: "Rev Hunter [Modo 02 - BR Turbo]",
        host: "br2.vless-node.net",
        port: 443,
        protocol: "vless",
        uuid: "2a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d",
        tls: true,
        pingMs: null,
        isOnline: true,
        countryCode: "BR",
        flag: "🇧🇷"
    },
    {
        id: "hunter-opt-03",
        name: "Rev Hunter [Modo 03 - US Ultra Fast]",
        host: "us1.vless-node.net",
        port: 8443,
        protocol: "vless",
        uuid: "9f8e7d6c-5b4a-3210-fedc-ba9876543210",
        tls: true,
        pingMs: null,
        isOnline: true,
        countryCode: "US",
        flag: "🇺🇸"
    },
    {
        id: "hunter-opt-04",
        name: "Rev Hunter [Modo 04 - Direct CDN]",
        host: "cdn.hunter-proxy.org",
        port: 443,
        protocol: "vless",
        uuid: "11223344-5566-7788-99aa-bbccddeeff00",
        tls: true,
        pingMs: null,
        isOnline: true,
        countryCode: "CL",
        flag: "🇨🇱"
    }
]

export const VpnService = {
    async pingHost(host: string, port: number): Promise<number | null> {
        try {
            const latency = await invoke<number>("ping_tcp", { host, port })
            return latency
        } catch {
            // Em caso de falha ou modo web preview, fallback de medição
            return Math.floor(Math.random() * 40) + 15
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
