use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};
use serde::{Deserialize, Serialize};
use tauri::Manager;

#[derive(Debug, Serialize, Deserialize)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
    pub protocol: String,
    pub uuid: Option<String>,
}

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
fn start_vpn_tunnel(server_config: ServerConfig) -> Result<bool, String> {
    println!("[Rev Hunter Core] Iniciando túnel para {:?}", server_config);
    Ok(true)
}

#[tauri::command]
fn stop_vpn_tunnel() -> Result<bool, String> {
    println!("[Rev Hunter Core] Finalizando túnel");
    Ok(true)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            if let Some(window) = app.get_webview_window("main") {
                if let Some(icon) = app.default_window_icon() {
                    let _ = window.set_icon(icon.clone());
                }
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            ping_tcp,
            start_vpn_tunnel,
            stop_vpn_tunnel
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
