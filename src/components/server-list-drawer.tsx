import * as React from "react"
import { LuX, LuRefreshCw, LuPlus, LuWifi, LuCheck, LuSearch } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import { VpnServer } from "@/types/vpn"
import { cn } from "@/lib/utils"

interface ServerListDrawerProps {
    isOpen: boolean
    onClose: () => void
    servers: VpnServer[]
    selectedServerId: string
    onSelectServer: (server: VpnServer) => void
    onPingAll: () => void
    isPinging: boolean
    onOpenAddSubscription: () => void
}

export const ServerListDrawer = ({
    isOpen,
    onClose,
    servers,
    selectedServerId,
    onSelectServer,
    onPingAll,
    isPinging,
    onOpenAddSubscription
}: ServerListDrawerProps) => {
    const [searchQuery, setSearchQuery] = React.useState("")

    if (!isOpen) return null

    const filteredServers = servers.filter((s) =>
        s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        s.host.toLowerCase().includes(searchQuery.toLowerCase())
    )

    const getPingColor = (ping: number | null) => {
        if (ping === null) return "text-slate-500"
        if (ping < 60) return "text-purple-400"
        if (ping < 120) return "text-amber-400"
        return "text-red-400"
    }

    const getPingBadgeVariant = (ping: number | null) => {
        if (ping === null) return "secondary"
        if (ping < 60) return "success"
        if (ping < 120) return "warning"
        return "destructive"
    }

    return (
        <div className="fixed inset-0 z-50 flex flex-col justify-end bg-black/80 backdrop-blur-md transition-opacity">
            <div className="flex h-[85vh] w-full flex-col rounded-t-3xl border-t border-purple-900/60 bg-slate-950 p-5 shadow-2xl">
                {/* Header */}
                <div className="flex items-center justify-between pb-4 border-b border-purple-900/40">
                    <div className="flex flex-col">
                        <Typography.H3 className="text-lg font-bold text-white">
                            Selecione o Modo / Servidor
                        </Typography.H3>
                        <Typography.Muted className="text-xs text-purple-300/70">
                            {servers.length} nós disponíveis • Conexão direta
                        </Typography.Muted>
                    </div>

                    <div className="flex items-center space-x-2">
                        <Button
                            variant="outline"
                            size="icon"
                            onClick={onPingAll}
                            disabled={isPinging}
                            className="h-9 w-9 rounded-xl border-purple-900/60 text-purple-200 hover:text-purple-400"
                            title="Testar Ping de Todos"
                        >
                            <LuRefreshCw className={cn("h-4 w-4", isPinging && "animate-spin text-purple-400")} />
                        </Button>
                        <Button
                            variant="hunter"
                            size="sm"
                            onClick={onOpenAddSubscription}
                            className="h-9 text-xs font-semibold px-3 space-x-1"
                        >
                            <LuPlus className="h-4 w-4" />
                            <Typography.Small className="text-white font-bold">Importar</Typography.Small>
                        </Button>
                        <Button
                            variant="ghost"
                            size="icon"
                            onClick={onClose}
                            className="h-9 w-9 rounded-xl text-purple-400 hover:text-white"
                        >
                            <LuX className="h-5 w-5" />
                        </Button>
                    </div>
                </div>

                {/* Search Bar */}
                <div className="relative mt-3 mb-2">
                    <LuSearch className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-purple-400/60" />
                    <input
                        type="text"
                        placeholder="Buscar servidor, país ou modo..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full rounded-xl border border-purple-900/50 bg-purple-950/40 py-2.5 pl-10 pr-4 text-xs text-white placeholder-purple-400/40 focus:border-purple-500 focus:outline-hidden"
                    />
                </div>

                {/* Server Items List */}
                <div className="mt-2 flex-1 space-y-2.5 overflow-y-auto pr-1">
                    {filteredServers.map((server) => {
                        const isSelected = server.id === selectedServerId

                        return (
                            <Card
                                key={server.id}
                                onClick={() => {
                                    onSelectServer(server)
                                    onClose()
                                }}
                                className={cn(
                                    "flex cursor-pointer items-center justify-between p-3.5 transition-all duration-200",
                                    isSelected
                                        ? "border-purple-500 bg-purple-950/60 shadow-md shadow-purple-500/20"
                                        : "border-purple-900/30 bg-purple-950/20 hover:border-purple-700/60 hover:bg-purple-950/40"
                                )}
                            >
                                <div className="flex items-center space-x-3">
                                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-900/40 border border-purple-800/40 text-xl shadow-inner">
                                        {server.flag ? server.flag : "🌐"}
                                    </div>
                                    <div className="flex flex-col">
                                        <div className="flex items-center space-x-1.5">
                                            <Typography.Large className={cn("text-xs font-semibold", isSelected ? "text-purple-300" : "text-white")}>
                                                {server.name}
                                            </Typography.Large>
                                            <Badge variant="outline" className="text-[9px] uppercase px-1 py-0 border-purple-800 text-purple-300">
                                                {server.protocol}
                                            </Badge>
                                        </div>
                                        <Typography.Muted className="text-[11px] font-mono text-purple-300/60">
                                            {server.host}:{server.port}
                                        </Typography.Muted>
                                    </div>
                                </div>

                                <div className="flex items-center space-x-2">
                                    <Badge variant={getPingBadgeVariant(server.pingMs)} className="flex items-center space-x-1 px-2 py-0.5">
                                        <LuWifi className={cn("h-3 w-3", getPingColor(server.pingMs))} />
                                        <Typography.Small className={cn("font-mono text-[11px] font-bold", getPingColor(server.pingMs))}>
                                            {server.pingMs !== null ? `${server.pingMs}ms` : "--"}
                                        </Typography.Small>
                                    </Badge>
                                    {isSelected && (
                                        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-purple-500 text-white shadow-xs shadow-purple-500/50">
                                            <LuCheck className="h-3.5 w-3.5 stroke-[3]" />
                                        </div>
                                    )}
                                </div>
                            </Card>
                        )
                    })}

                    {filteredServers.length === 0 && (
                        <div className="flex flex-col items-center justify-center py-12 text-center">
                            <Typography.Muted className="text-sm text-purple-400/50">
                                Nenhum servidor encontrado para "{searchQuery}"
                            </Typography.Muted>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
