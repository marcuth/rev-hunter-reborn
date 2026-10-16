use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, Runtime};

#[cfg(target_os = "android")]
use tauri::plugin::PluginHandle;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
    pub protocol: String,
    pub uuid: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VpnStatusResponse {
    pub is_running: bool,
    pub bytes_up: u64,
    pub bytes_down: u64,
}

#[cfg(target_os = "android")]
pub struct VpnHandle<R: Runtime>(pub PluginHandle<R>);

#[tauri::command]
fn ping_tcp(host: String, port: u16) -> Result<u64, String> {
    let target = format!("{}:{}", host, port);
    let start = Instant::now();
    let timeout = Duration::from_millis(1500);

    let mut addrs = match target.to_socket_addrs() {
        Ok(a) => a,
        Err(_) => return Err(format!("Falha ao resolver DNS: {}", host)),
    };

    if let Some(addr) = addrs.next() {
        match TcpStream::connect_timeout(&addr, timeout) {
            Ok(_) => {
                let duration = start.elapsed().as_millis() as u64;
                Ok(duration)
            }
            Err(e) => Err(format!("Timeout ou erro de conexão: {}", e)),
        }
    } else {
        Err("Endereço IP não encontrado".into())
    }
}

#[tauri::command]
async fn start_vpn_tunnel<R: Runtime>(app: AppHandle<R>, server_config: ServerConfig) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        if let Some(handle) = app.try_state::<VpnHandle<R>>() {
            #[derive(Serialize)]
            struct AndroidStartArgs {
                host: String,
                port: u16,
                protocol: String,
                uuid: Option<String>,
            }
            let args = AndroidStartArgs {
                host: server_config.host.clone(),
                port: server_config.port,
                protocol: server_config.protocol.clone(),
                uuid: server_config.uuid.clone(),
            };
            handle
                .0
                .run_mobile_plugin::<serde_json::Value>("startVpn", args)
                .map_err(|e| format!("Erro no VpnService Android: {}", e))?;
            return Ok(true);
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        println!("[Desktop/Dev Core] Túnel VPN simulado para {:?}", server_config);
        let _ = (app, server_config);
    }

    Ok(true)
}

#[tauri::command]
async fn stop_vpn_tunnel<R: Runtime>(app: AppHandle<R>) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        if let Some(handle) = app.try_state::<VpnHandle<R>>() {
            handle
                .0
                .run_mobile_plugin::<serde_json::Value>("stopVpn", ())
                .map_err(|e| format!("Erro ao parar VPN Android: {}", e))?;
            return Ok(true);
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        println!("[Desktop/Dev Core] Túnel VPN encerrado");
        let _ = app;
    }

    Ok(true)
}

#[tauri::command]
async fn get_vpn_status<R: Runtime>(app: AppHandle<R>) -> Result<VpnStatusResponse, String> {
    #[cfg(target_os = "android")]
    {
        if let Some(handle) = app.try_state::<VpnHandle<R>>() {
            #[derive(Deserialize)]
            #[serde(rename_all = "camelCase")]
            struct AndroidStatus {
                is_running: bool,
                bytes_up: u64,
                bytes_down: u64,
            }
            if let Ok(status) = handle.0.run_mobile_plugin::<AndroidStatus>("getVpnStatus", ()) {
                return Ok(VpnStatusResponse {
                    is_running: status.is_running,
                    bytes_up: status.bytes_up,
                    bytes_down: status.bytes_down,
                });
            }
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
    }

    Ok(VpnStatusResponse {
        is_running: false,
        bytes_up: 0,
        bytes_down: 0,
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(
            tauri::plugin::Builder::<_, ()>::new("vpn")
                .setup(|app, api| {
                    #[cfg(target_os = "android")]
                    {
                        let handle = api.register_android_plugin("com.hunter.reborn", "VpnPlugin")?;
                        app.manage(VpnHandle(handle));
                    }
                    let _ = (app, api);
                    Ok(())
                })
                .build(),
        )
        .setup(|_app| {
            #[cfg(desktop)]
            {
                if let Some(window) = _app.get_webview_window("main") {
                    if let Some(icon) = _app.default_window_icon() {
                        let _ = window.set_icon(icon.clone());
                    }
                }
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            ping_tcp,
            start_vpn_tunnel,
            stop_vpn_tunnel,
            get_vpn_status
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

