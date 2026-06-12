mod app;
mod collection;
mod db;

use anyhow::{Context, Result};
use app::App;
use collection::CollectionManager;
use db::Database;
use lab7_shared::{read_env, read_frame, write_frame, Request, Response};
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::RwLock;

async fn handle_client(app: Arc<App>, mut stream: TcpStream) -> Result<()> {
    let request: Request = read_frame(&mut stream).await?;
    println!("SERVER GOT REQUEST: {request:?}");
    let response = app.handle(request).await;
    println!("SERVER RESPONSE: {response:?}");
    write_frame(&mut stream, &response).await
}

#[tokio::main]
async fn main() -> Result<()> {
    let env = read_env(".env")?;
    let host = env
        .get("HOST_NAME")
        .context("hostname should be specified in env")?
        .clone();
    let port: u16 = env
        .get("SERVER_PORT")
        .context("server port should be specified in env")?
        .parse()?;
    let gw_host = env
        .get("GW_HOST")
        .context("GW_HOST should be specified in env")?
        .clone();
    let gw_port: u16 = env
        .get("GW_PORT")
        .context("GW_PORT should be specified in env")?
        .parse()?;

    let collection = Arc::new(RwLock::new(CollectionManager::default()));
    let db = Database::new(&env, collection.clone())?;
    collection
        .write()
        .await
        .upload(db.download_collection().await?);
    let app = Arc::new(App { db, collection });

    if let Ok(mut gateway) = TcpStream::connect((gw_host.as_str(), gw_port)).await {
        let _ = write_frame(
            &mut gateway,
            &Request::HiBalancer {
                host: host.clone(),
                port,
            },
        )
        .await;
        let _: Result<Response> = read_frame(&mut gateway).await;
    }

    let listener = TcpListener::bind((host.as_str(), port)).await?;
    println!("Server started at {host}:{port}");
    loop {
        let app = app.clone();
        match listener.accept().await {
            Ok((stream, _addr)) => {
                tokio::spawn(async move {
                    if let Err(err) = handle_client(app, stream).await {
                        eprintln!("{err:?}");
                    }
                });
            }
            Err(err) => eprintln!("{err:?}"),
        }
    }
}
