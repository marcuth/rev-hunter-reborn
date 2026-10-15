import { LuArrowDown, LuArrowUp, LuClock, LuZap } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { Card } from "@/components/ui/card"
import { VpnStats } from "@/types/vpn"

interface TrafficStatsProps {
    stats: VpnStats
    isConnected: boolean
}

export const TrafficStats = ({ stats, isConnected }: TrafficStatsProps) => {
    const formatDuration = (seconds: number) => {
        const h = Math.floor(seconds / 3600).toString().padStart(2, "0")
        const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, "0")
        const s = (seconds % 60).toString().padStart(2, "0")
        return `${h}:${m}:${s}`
    }

    return (
        <div className="grid grid-cols-2 gap-3">
            {/* Download Card */}
            <Card className="border-purple-900/40 bg-purple-950/25 p-3.5 backdrop-blur-sm">
                <div className="flex items-center space-x-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-purple-500/15 text-purple-400">
                        <LuArrowDown className="h-5 w-5" />
                    </div>
                    <div className="flex flex-col">
                        <Typography.Muted className="text-[10px] font-semibold uppercase tracking-wider text-purple-300/80">
                            Download
                        </Typography.Muted>
                        <Typography.Large className="text-sm font-bold text-white font-mono">
                            {isConnected ? stats.downloadSpeed : "0.0 KB/s"}
                        </Typography.Large>
                    </div>
                </div>
                <div className="mt-2 flex items-center justify-between border-t border-purple-900/40 pt-1.5">
                    <Typography.Muted className="text-[10px] text-purple-400/60">
                        Total
                    </Typography.Muted>
                    <Typography.Small className="text-[11px] font-mono text-purple-200">
                        {isConnected ? stats.totalDownload : "0 MB"}
                    </Typography.Small>
                </div>
            </Card>

            {/* Upload Card */}
            <Card className="border-purple-900/40 bg-purple-950/25 p-3.5 backdrop-blur-sm">
                <div className="flex items-center space-x-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-fuchsia-500/15 text-fuchsia-400">
                        <LuArrowUp className="h-5 w-5" />
                    </div>
                    <div className="flex flex-col">
                        <Typography.Muted className="text-[10px] font-semibold uppercase tracking-wider text-purple-300/80">
                            Upload
                        </Typography.Muted>
                        <Typography.Large className="text-sm font-bold text-white font-mono">
                            {isConnected ? stats.uploadSpeed : "0.0 KB/s"}
                        </Typography.Large>
                    </div>
                </div>
                <div className="mt-2 flex items-center justify-between border-t border-purple-900/40 pt-1.5">
                    <Typography.Muted className="text-[10px] text-purple-400/60">
                        Total
                    </Typography.Muted>
                    <Typography.Small className="text-[11px] font-mono text-purple-200">
                        {isConnected ? stats.totalUpload : "0 MB"}
                    </Typography.Small>
                </div>
            </Card>

            {/* Duration (Unlimited) */}
            <Card className="border-purple-900/40 bg-purple-950/25 p-3.5 backdrop-blur-sm">
                <div className="flex items-center space-x-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400">
                        <LuClock className="h-5 w-5" />
                    </div>
                    <div className="flex flex-col">
                        <Typography.Muted className="text-[10px] font-semibold uppercase tracking-wider text-purple-300/80">
                            Tempo Conectado
                        </Typography.Muted>
                        <Typography.Large className="text-sm font-bold text-white font-mono">
                            {isConnected ? formatDuration(stats.durationSeconds) : "00:00:00"}
                        </Typography.Large>
                    </div>
                </div>
                <div className="mt-2 flex items-center justify-between border-t border-purple-900/40 pt-1.5">
                    <Typography.Muted className="text-[10px] text-purple-400 font-semibold">
                        ILIMITADO (SEM ADS)
                    </Typography.Muted>
                    <div className="h-1.5 w-1.5 rounded-full bg-purple-400 animate-pulse" />
                </div>
            </Card>

            {/* Engine Status */}
            <Card className="border-purple-900/40 bg-purple-950/25 p-3.5 backdrop-blur-sm">
                <div className="flex items-center space-x-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-purple-500/15 text-purple-400">
                        <LuZap className="h-5 w-5" />
                    </div>
                    <div className="flex flex-col">
                        <Typography.Muted className="text-[10px] font-semibold uppercase tracking-wider text-purple-300/80">
                            Protocolo Core
                        </Typography.Muted>
                        <Typography.Large className="text-sm font-bold text-white font-mono">
                            VLESS • XTLS
                        </Typography.Large>
                    </div>
                </div>
                <div className="mt-2 flex items-center justify-between border-t border-purple-900/40 pt-1.5">
                    <Typography.Muted className="text-[10px] text-purple-400/60">
                        TUN Device
                    </Typography.Muted>
                    <Typography.Small className="text-[11px] font-mono text-purple-200">
                        tun0 (Direct)
                    </Typography.Small>
                </div>
            </Card>
        </div>
    )
}
