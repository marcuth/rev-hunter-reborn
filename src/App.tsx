import * as React from "react"
import { LuSettings2, LuRadio, LuSparkles } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConnectionButton } from "@/components/connection-button"
import { ServerCard } from "@/components/server-card"
import { TrafficStats } from "@/components/traffic-stats"
import { ServerListDrawer } from "@/components/server-list-drawer"
import { SubscriptionModal } from "@/components/subscription-modal"
import { VpnConnectionStatus, VpnServer, VpnStats } from "@/types/vpn"
import { DEFAULT_SERVERS, VpnService } from "@/services/vpn-service"

export function App() {
    const [servers, setServers] = React.useState<VpnServer[]>(DEFAULT_SERVERS)
    const [selectedServer, setSelectedServer] = React.useState<VpnServer>(DEFAULT_SERVERS[0])
    const [status, setStatus] = React.useState<VpnConnectionStatus>("disconnected")
    const [isServerDrawerOpen, setIsServerDrawerOpen] = React.useState(false)
    const [isSubscriptionModalOpen, setIsSubscriptionModalOpen] = React.useState(false)
    const [isPinging, setIsPinging] = React.useState(false)

    const [stats, setStats] = React.useState<VpnStats>({
        uploadSpeed: "0.0 KB/s",
        downloadSpeed: "0.0 KB/s",
        totalUpload: "0.0 MB",
        totalDownload: "0.0 MB",
        durationSeconds: 0,
        currentPingMs: null
    })

    // Timer de conexão e telemetria de rede (TUN nativo Android / Desktop fallback)
    React.useEffect(() => {
        let interval: ReturnType<typeof setInterval> | null = null

        if (status === "connected") {
            let lastBytesUp = 0
            let lastBytesDown = 0
            let lastTime = Date.now()

            interval = setInterval(async () => {
                const liveStatus = await VpnService.getVpnStatus()
                const now = Date.now()
                const timeDiff = Math.max((now - lastTime) / 1000, 1)
                lastTime = now

                setStats((prev) => {
                    const addedSec = prev.durationSeconds + 1
                    let upSpeed = "0.0 KB/s"
                    let downSpeed = "0.0 KB/s"
                    let totalUpMb = prev.totalUpload
                    let totalDownMb = prev.totalDownload

                    if (liveStatus.bytesUp > 0 || liveStatus.bytesDown > 0) {
                        const deltaUp = Math.max(liveStatus.bytesUp - lastBytesUp, 0)
                        const deltaDown = Math.max(liveStatus.bytesDown - lastBytesDown, 0)
                        lastBytesUp = liveStatus.bytesUp
                        lastBytesDown = liveStatus.bytesDown

                        upSpeed = `${(deltaUp / 1024 / timeDiff).toFixed(1)} KB/s`
                        downSpeed = `${(deltaDown / 1024 / timeDiff).toFixed(1)} KB/s`
                        totalUpMb = `${(liveStatus.bytesUp / (1024 * 1024)).toFixed(2)} MB`
                        totalDownMb = `${(liveStatus.bytesDown / (1024 * 1024)).toFixed(2)} MB`
                    }

                    return {
                        uploadSpeed: upSpeed,
                        downloadSpeed: downSpeed,
                        totalUpload: totalUpMb,
                        totalDownload: totalDownMb,
                        durationSeconds: addedSec,
                        currentPingMs: selectedServer.pingMs || 32
                    }
                })
            }, 1000)
        } else {
            setStats({
                uploadSpeed: "0.0 KB/s",
                downloadSpeed: "0.0 KB/s",
                totalUpload: "0.0 MB",
                totalDownload: "0.0 MB",
                durationSeconds: 0,
                currentPingMs: null
            })
        }

        return () => {
            if (interval) clearInterval(interval)
        }
    }, [status, selectedServer])

    // Testa ping dos servidores na inicialização
    const handlePingAll = React.useCallback(async () => {
        setIsPinging(true)
        // Garante ao menos um ciclo de render com isPinging=true visível
        // mesmo quando os pings retornam erro de DNS instantaneamente
        const [updated] = await Promise.all([
            VpnService.pingAll(servers),
            new Promise((resolve) => setTimeout(resolve, 500))
        ])
        setServers(updated as typeof servers)
        const current = (updated as typeof servers).find((s) => s.id === selectedServer.id)
        if (current) setSelectedServer(current)
        setIsPinging(false)
    }, [servers, selectedServer])

    React.useEffect(() => {
        handlePingAll()
    }, [])

    const handleToggleConnection = async () => {
        if (status === "disconnected") {
            setStatus("connecting")
            try {
                const success = await VpnService.connectTunnel(selectedServer)
                if (success) {
                    setStatus("connected")
                } else {
                    setStatus("disconnected")
                }
            } catch (err: unknown) {
                console.error("Erro ao ativar VPN:", err)
                setStatus("disconnected")
                const msg = typeof err === "string" ? err : (err as Error)?.message || "Permissão de VPN recusada pelo usuário."
                alert(msg)
            }
        } else if (status === "connected") {
            setStatus("disconnecting")
            try {
                await VpnService.disconnectTunnel()
            } catch (err) {
                console.error("Erro ao desconectar VPN:", err)
            } finally {
                setStatus("disconnected")
            }
        }
    }

    const handleServersImported = (imported: VpnServer[]) => {
        setServers(imported)
        if (imported.length > 0) {
            setSelectedServer(imported[0])
        }
        handlePingAll()
    }

    return (
        <div className="relative flex min-h-screen flex-col bg-slate-950 text-slate-100 selection:bg-purple-600 selection:text-white">
            {/* Background Cyber Glow (Deep Purple) */}
            <div className="pointer-events-none absolute -top-40 left-1/2 -z-10 h-96 w-96 -translate-x-1/2 rounded-full bg-purple-600/25 blur-[130px]" />
            <div className="pointer-events-none absolute bottom-0 right-0 -z-10 h-80 w-80 rounded-full bg-fuchsia-600/10 blur-[120px]" />

            {/* Top Navigation Bar */}
            <header className="flex items-center justify-between px-5 pt-5 pb-3">
                <div className="flex items-center space-x-3">
                    <div className="relative flex h-11 w-11 items-center justify-center overflow-hidden rounded-xl border border-purple-500/40 bg-purple-950/60 shadow-lg shadow-purple-500/20">
                        <img src="/icon.png" alt="Rev Hunter Logo" className="h-full w-full object-cover" />
                    </div>
                    <div className="flex flex-col">
                        <div className="flex items-center space-x-1.5">
                            <Typography.Large className="text-sm font-extrabold tracking-tight text-white uppercase">
                                REV HUNTER
                            </Typography.Large>
                            <Badge variant="default" className="text-[9px] font-bold py-0 px-1.5 bg-purple-500/25 text-purple-300 border border-purple-500/40">
                                REBORN
                            </Badge>
                        </div>
                        <Typography.Muted className="text-[10px] text-purple-300/70 font-mono">
                            MOD • ZERO ANÚNCIOS • ILIMITADO
                        </Typography.Muted>
                    </div>
                </div>

                <div className="flex items-center space-x-2">
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setIsSubscriptionModalOpen(true)}
                        className="h-9 w-9 rounded-xl border-purple-900/60 text-purple-300 hover:text-white hover:border-purple-600"
                        title="Importar Lista / Subscrição"
                    >
                        <LuSettings2 className="h-4 w-4" />
                    </Button>
                </div>
            </header>

            {/* Main Body */}
            <main className="flex flex-1 flex-col justify-between px-5 pb-6 pt-2">
                {/* Status Hero & Central Button */}
                <div className="flex flex-col items-center justify-center">
                    <ConnectionButton
                        status={status}
                        onToggle={handleToggleConnection}
                    />
                </div>

                {/* Bottom Panels (Server Card & Traffic Stats) */}
                <div className="space-y-3.5 mt-auto">
                    {/* Server Selection Card */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between px-1">
                            <Typography.Muted className="text-[11px] font-bold uppercase tracking-wider text-purple-300/80">
                                SERVIDOR ATIVO
                            </Typography.Muted>
                            <div className="flex items-center space-x-1 text-purple-400">
                                <LuRadio className="h-3 w-3 animate-pulse" />
                                <Typography.Small className="text-[10px] font-semibold text-purple-300">
                                    {servers.length} Modos
                                </Typography.Small>
                            </div>
                        </div>

                        <ServerCard
                            server={selectedServer}
                            onClick={() => setIsServerDrawerOpen(true)}
                        />
                    </div>

                    {/* Network Traffic & Time Stats */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between px-1">
                            <Typography.Muted className="text-[11px] font-bold uppercase tracking-wider text-purple-300/80">
                                TELEMETRIA DA CONEXÃO
                            </Typography.Muted>
                            <div className="flex items-center space-x-1 text-purple-400">
                                <LuSparkles className="h-3 w-3 text-fuchsia-400" />
                                <Typography.Small className="text-[10px] text-fuchsia-300 font-medium">
                                    Sem Rastreadores
                                </Typography.Small>
                            </div>
                        </div>

                        <TrafficStats
                            stats={stats}
                            isConnected={status === "connected"}
                        />
                    </div>
                </div>
            </main>

            {/* Drawers and Modals */}
            <ServerListDrawer
                isOpen={isServerDrawerOpen}
                onClose={() => setIsServerDrawerOpen(false)}
                servers={servers}
                selectedServerId={selectedServer.id}
                onSelectServer={(srv) => setSelectedServer(srv)}
                onPingAll={handlePingAll}
                isPinging={isPinging}
                onOpenAddSubscription={() => {
                    setIsServerDrawerOpen(false)
                    setIsSubscriptionModalOpen(true)
                }}
            />

            <SubscriptionModal
                isOpen={isSubscriptionModalOpen}
                onClose={() => setIsSubscriptionModalOpen(false)}
                onServersImported={handleServersImported}
            />
        </div>
    )
}

export default App
