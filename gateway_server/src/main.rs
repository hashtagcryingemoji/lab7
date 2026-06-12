mod balancer;

use anyhow::{Context, Result};
use balancer::Balancer;
use lab7_shared::{read_env, read_frame, write_frame, Request};
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::Mutex;

async fn handle_client(balancer: Arc<Mutex<Balancer>>, mut stream: TcpStream) -> Result<()> {
    let request: Request = read_frame(&mut stream).await?;
    let response = balancer.lock().await.handle(request).await;
    write_frame(&mut stream, &response).await
}

#[tokio::main]
async fn main() -> Result<()> {
    let env = read_env(".env")?;
    let host = env
        .get("GW_HOST")
        .context("check for GW_HOST in .env")?
        .clone();
    let port: u16 = env
        .get("GW_PORT")
        .context("check for GW_PORT in .env")?
        .parse()?;
    let listener = TcpListener::bind((host.as_str(), port)).await?;
    let balancer = Arc::new(Mutex::new(Balancer::default()));

    println!("Gateway started at {host}:{port}");
    loop {
        match listener.accept().await {
            Ok((stream, _addr)) => {
                let balancer = balancer.clone();
                tokio::spawn(async move {
                    if let Err(err) = handle_client(balancer, stream).await {
                        eprintln!("{err:?}");
                    }
                });
            }
            Err(err) => eprintln!("{err:?}"),
        }
    }
}
