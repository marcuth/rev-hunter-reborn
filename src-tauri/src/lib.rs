use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, Runtime};
use tun2proxy::{Args, ArgDns, general_run_async};
use tokio_util::sync::CancellationToken;

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

use std::str::FromStr;
use std::sync::Mutex;

#[derive(Default)]
pub struct VpnEngineState {
    pub cancel_token: Mutex<Option<CancellationToken>>,
}

#[cfg(target_os = "android")]
pub struct VpnHandle<R: Runtime>(pub PluginHandle<R>);

#[tauri::command]
async fn ping_tcp(host: String, port: u16) -> Result<u64, String> {
    tokio::task::spawn_blocking(move || {
        let target = format!("{}:{}", host, port);
        let start = Instant::now();
        let timeout = Duration::from_millis(1500);

        let mut addrs = match target.to_socket_addrs() {
            Ok(a) => a,
            Err(e) => return Err(format!("Falha ao resolver DNS: {}", e)),
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
    })
    .await
    .map_err(|e| format!("Erro interno de execução: {}", e))?
}

#[tauri::command]
async fn start_vpn_tunnel<R: Runtime>(app: AppHandle<R>, server_config: ServerConfig) -> Result<bool, String> {
    let cancel_token = CancellationToken::new();
    if let Some(state) = app.try_state::<VpnEngineState>() {
        let mut lock = state.cancel_token.lock().unwrap();
        if let Some(prev) = lock.take() {
            prev.cancel();
        }
        *lock = Some(cancel_token.clone());
    }

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
            let response = handle
                .0
                .run_mobile_plugin::<serde_json::Value>("startVpn", args)
                .map_err(|e| format!("Erro no VpnService Android: {}", e))?;

            let tun_fd = response.get("fd").and_then(|v| v.as_i64()).map(|v| v as i32);

            if let Some(fd) = tun_fd {
                if fd > 0 {
                    let mut proxy_args = Args::default();
                    // Conecta diretamente no forwarder SOCKS5 local na porta 1080 aberto pelo serviço SSH
                    let proxy_url = "socks5://127.0.0.1:1080".to_string();
                    if let Ok(proxy_params) = tun2proxy::ProxyParameters::from_str(&proxy_url) {
                        proxy_args.proxy = proxy_params;
                    }
                    proxy_args.dns = ArgDns::OverTcp;
                    proxy_args.tun_fd = Some(fd);

                    let token = cancel_token.clone();
                    tokio::spawn(async move {
                        println!("[Rev Hunter Core] tun2proxy rodando no FD {} direcionado para 127.0.0.1:1080...", fd);
                        if let Err(e) = general_run_async(proxy_args, 1500, false, token).await {
                            eprintln!("[Rev Hunter Core] tun2proxy aviso/erro: {:?}", e);
                        }
                        println!("[Rev Hunter Core] tun2proxy finalizado com segurança.");
                    });
                    return Ok(true);
                }
            }

            return Err("A interface VPN TUN não retornou um descritor válido.".into());
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        println!("[Desktop/Dev Core] Túnel VPN inicializado com tun2proxy para {:?}", server_config);
        let mut proxy_args = Args::default();
        let proxy_url = format!("socks5://{}:{}", server_config.host, server_config.port);
        if let Ok(proxy_params) = tun2proxy::ProxyParameters::from_str(&proxy_url) {
            proxy_args.proxy = proxy_params;
        }
        proxy_args.dns = ArgDns::OverTcp;
        let token = cancel_token.clone();
        tokio::spawn(async move {
            println!("[Desktop/Dev Core] tun2proxy ativo em segundo plano...");
            let _ = general_run_async(proxy_args, 1500, false, token).await;
        });
    }

    Ok(true)
}

#[tauri::command]
async fn stop_vpn_tunnel<R: Runtime>(app: AppHandle<R>) -> Result<bool, String> {
    if let Some(state) = app.try_state::<VpnEngineState>() {
        let mut lock = state.cancel_token.lock().unwrap();
        if let Some(token) = lock.take() {
            token.cancel();
            println!("[Rev Hunter Core] Token de cancelamento do tun2proxy disparado.");
        }
    }

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
            _app.manage(VpnEngineState::default());
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

