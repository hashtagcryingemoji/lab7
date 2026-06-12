mod app;
mod script_manager;

use anyhow::{anyhow, Context, Result};
use app::{read_prompt, ClientApp};
use lab7_shared::{md2_hash, read_env, EnterType, Request};
use std::time::Duration;

async fn authenticate(app: &mut ClientApp) -> Result<()> {
    println!("Введите 1, чтобы зарегистрироваться, 2, чтобы войти:");
    let enter_type = match read_prompt("> ")?.parse::<u8>().ok() {
        Some(1) => EnterType::Register,
        Some(2) => EnterType::Login,
        _ => return Err(anyhow!("invalid input")),
    };
    println!("Введите логин: ");
    let login = read_prompt("> ")?;
    println!("Введите пароль: ");
    let password = read_prompt("> ")?;
    let response = app
        .send(Request::HandShake {
            user_hash: format!("{} {}", md2_hash(&password), login),
            enter_type,
        })
        .await?;
    app.resolve_response(response)?;
    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    let env = read_env(".env")?;
    let mut app = ClientApp::new(
        env.get("GW_HOST")
            .context("hostname should be specified in env")?
            .clone(),
        env.get("GW_PORT")
            .context("server port should be specified in env")?
            .parse()?,
    );

    let mut timeout = Duration::from_secs(5);
    loop {
        match authenticate(&mut app).await {
            Ok(()) => break,
            Err(err) => {
                println!("cannot connect to server: {err}");
                tokio::time::sleep(timeout).await;
                timeout = (timeout + Duration::from_secs(1)).min(Duration::from_secs(50));
            }
        }
    }

    loop {
        let command = read_prompt("> ")?;
        match app.run_command(command.trim()).await {
            Ok(true) => {}
            Ok(false) => break,
            Err(err) => println!("{err}"),
        }
    }
    Ok(())
}
