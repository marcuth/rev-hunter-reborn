import { LuServer, LuChevronRight, LuWifi } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import { VpnServer } from "@/types/vpn"
import { cn } from "@/lib/utils"

interface ServerCardProps {
    server: VpnServer
    onClick: () => void
}

export const ServerCard = ({ server, onClick }: ServerCardProps) => {
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
        <Card
            onClick={onClick}
            className="group relative cursor-pointer overflow-hidden border-purple-900/50 bg-purple-950/30 p-4 transition-all duration-200 hover:border-purple-500/60 hover:bg-purple-950/50 active:scale-[0.99]"
        >
            <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3.5">
                    {/* Flag / Server Icon */}
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-purple-950/60 border border-purple-800/50 text-2xl shadow-inner group-hover:border-purple-500/60 transition-colors">
                        {server.flag ? server.flag : <LuServer className="h-6 w-6 text-purple-400" />}
                    </div>

                    <div className="flex flex-col space-y-0.5">
                        <div className="flex items-center space-x-2">
                            <Typography.Large className="text-sm font-semibold tracking-tight text-white group-hover:text-purple-300 transition-colors">
                                {server.name}
                            </Typography.Large>
                            <Badge variant="outline" className="text-[10px] uppercase font-mono py-0 px-1.5 border-purple-800 text-purple-300">
                                {server.protocol}
                            </Badge>
                        </div>
                        <Typography.Muted className="text-xs text-purple-300/70 font-mono">
                            {server.host}:{server.port}
                        </Typography.Muted>
                    </div>
                </div>

                <div className="flex items-center space-x-2.5">
                    <Badge variant={getPingBadgeVariant(server.pingMs)} className="flex items-center space-x-1 px-2 py-0.5">
                        <LuWifi className={cn("h-3 w-3", getPingColor(server.pingMs))} />
                        <Typography.Small className={cn("font-mono text-[11px] font-bold", getPingColor(server.pingMs))}>
                            {server.pingMs !== null ? `${server.pingMs}ms` : "--"}
                        </Typography.Small>
                    </Badge>
                    <LuChevronRight className="h-5 w-5 text-purple-400/60 transition-transform group-hover:translate-x-0.5 group-hover:text-purple-300" />
                </div>
            </div>
        </Card>
    )
}
