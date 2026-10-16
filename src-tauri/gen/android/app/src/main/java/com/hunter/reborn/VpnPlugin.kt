package com.hunter.reborn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin

@InvokeArg
class StartVpnArgs {
    var host: String = "br1.vless-node.net"
    var port: Int = 443
    var protocol: String = "vless"
    var uuid: String? = null
}

@TauriPlugin
class VpnPlugin(private val activity: Activity) : Plugin(activity) {

    private var pendingInvoke: Invoke? = null
    private var pendingHost: String = ""
    private var pendingPort: Int = 443
    private var pendingProtocol: String = "vless"

    // Launcher AndroidX correto para receber o resultado do diálogo de permissão VPN
    private val vpnPermissionLauncher: ActivityResultLauncher<Intent> =
        (activity as androidx.activity.ComponentActivity).activityResultRegistry.register(
            "vpn_permission_launcher",
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            handlePermissionResult(result)
        }

    @Command
    fun startVpn(invoke: Invoke) {
        val args = try {
            invoke.parseArgs(StartVpnArgs::class.java)
        } catch (_: Exception) {
            StartVpnArgs()
        }

        val host = args.host
        val port = args.port
        val protocol = args.protocol

        pendingHost = host
        pendingPort = port
        pendingProtocol = protocol

        val prepareIntent = VpnService.prepare(activity)
        if (prepareIntent != null) {
            // Abre o diálogo nativo do Android para solicitar permissão de VPN
            pendingInvoke = invoke
            activity.runOnUiThread {
                vpnPermissionLauncher.launch(prepareIntent)
            }
        } else {
            // Permissão já concedida anteriormente — inicia o serviço diretamente
            launchVpnService(host, port, protocol)
            resolveVpnStarted(invoke)
        }
    }

    private fun handlePermissionResult(result: ActivityResult) {
        val currentInvoke = pendingInvoke ?: return
        pendingInvoke = null

        if (result.resultCode == Activity.RESULT_OK) {
            launchVpnService(pendingHost, pendingPort, pendingProtocol)
            resolveVpnStarted(currentInvoke)
        } else {
            currentInvoke.reject("Permissão de VPN recusada pelo usuário.")
        }
    }

    private fun resolveVpnStarted(invoke: Invoke) {
        // Aguarda o HunterVpnService estabelecer a interface TUN em background
        // para obter o file descriptor antes de responder ao Rust/tun2proxy
        Thread {
            var waited = 0
            while (HunterVpnService.lastFd <= 0 && waited < 10000) {
                Thread.sleep(50)
                waited += 50
            }
            val res = JSObject()
            val fd = HunterVpnService.lastFd
            res.put("success", fd > 0)
            res.put("status", if (fd > 0) "connected" else "failed")
            res.put("fd", fd)
            invoke.resolve(res)
        }.start()
    }

    @Command
    fun stopVpn(invoke: Invoke) {
        val intent = Intent(activity, HunterVpnService::class.java).apply {
            action = HunterVpnService.ACTION_DISCONNECT
        }
        activity.startService(intent)

        val res = JSObject()
        res.put("success", true)
        res.put("status", "disconnected")
        invoke.resolve(res)
    }

    @Command
    fun getVpnStatus(invoke: Invoke) {
        val res = JSObject()
        res.put("isRunning", HunterVpnService.isRunning)
        res.put("bytesUp", HunterVpnService.bytesUp.get())
        res.put("bytesDown", HunterVpnService.bytesDown.get())
        invoke.resolve(res)
    }

    private fun launchVpnService(host: String, port: Int, protocol: String) {
        val intent = Intent(activity, HunterVpnService::class.java).apply {
            action = HunterVpnService.ACTION_CONNECT
            putExtra("host", host)
            putExtra("port", port)
            putExtra("protocol", protocol)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }
}
