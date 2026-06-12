use crate::script_manager::ScriptManager;
use anyhow::{anyhow, Context, Result};
use lab7_shared::{read_frame, write_frame, CommandSyntax, Request, Response};
use std::collections::HashMap;
use std::io::{self, Write};
use tokio::net::TcpStream;

pub struct ClientApp {
    pub host: String,
    pub port: u16,
    pub token: String,
    commands: HashMap<String, CommandSyntax>,
    scripts: ScriptManager,
}

impl ClientApp {
    pub fn new(host: String, port: u16) -> Self {
        Self {
            host,
            port,
            token: String::new(),
            commands: HashMap::new(),
            scripts: ScriptManager::new(),
        }
    }

    async fn connect(&self) -> Result<TcpStream> {
        Ok(TcpStream::connect((self.host.as_str(), self.port)).await?)
    }

    pub async fn send(&self, request: Request) -> Result<Response> {
        let mut stream = self.connect().await?;
        write_frame(&mut stream, &request).await?;
        read_frame(&mut stream).await
    }

    pub fn resolve_response(&mut self, response: Response) -> Result<bool> {
        match response {
            Response::Info { message } => println!("{message}"),
            Response::Error { message } => println!("ошибка: {message}"),
            Response::ResetTokenPlease => return Err(anyhow!("Переподключение...")),
            Response::Shutdown => return Ok(false),
            Response::HandShake { commands, token } => {
                self.commands = commands
                    .into_iter()
                    .map(|command| (command.name.clone(), command))
                    .collect();
                self.token = token;
            }
            Response::Pong => {}
        }
        Ok(true)
    }

    pub async fn run_command(&mut self, command: &str) -> Result<bool> {
        match command {
            "help" => {
                for syntax in self.commands.values() {
                    println!("{} - {}", syntax.name, syntax.description);
                }
                println!("help - Выводит справку о текущих командах.");
                println!("exit - Завершает процесс клиента");
                println!("execute_script - выполняет указанный скрипт");
                Ok(true)
            }
            "exit" => {
                println!("Программа завершается...");
                Ok(false)
            }
            "execute_script" => {
                let path = read_prompt("")?;
                self.scripts.add(path)?;
                if !self.scripts.running {
                    self.resolve_script().await
                } else {
                    Ok(true)
                }
            }
            other => {
                let syntax = self
                    .commands
                    .get(other)
                    .cloned()
                    .ok_or_else(|| anyhow!("Неизвестная комманда: {other}"))?;
                let mut args = Vec::new();
                for arg in &syntax.args {
                    args.push(read_prompt(&format!("{arg}: "))?);
                }
                let response = self
                    .send(Request::ExecuteCommand {
                        user_token: self.token.clone(),
                        command_name: other.to_string(),
                        args,
                    })
                    .await?;
                self.resolve_response(response)
            }
        }
    }

    async fn resolve_script(&mut self) -> Result<bool> {
        self.scripts.running = true;
        while let Some(command) = self.scripts.get_line() {
            if command == "execute_script" {
                let name = self.scripts.get_line().context("script name is required")?;
                self.scripts.add(name)?;
                continue;
            }
            let syntax = self
                .commands
                .get(&command)
                .cloned()
                .ok_or_else(|| anyhow!("Неизвестная комманда: {command}"))?;
            let mut args = Vec::new();
            for _ in &syntax.args {
                args.push(
                    self.scripts
                        .get_line()
                        .context("script command argument is required")?,
                );
            }
            let response = self
                .send(Request::ExecuteCommand {
                    user_token: self.token.clone(),
                    command_name: command,
                    args,
                })
                .await?;
            if let Response::Error { message } = &response {
                self.scripts.panic();
                return Err(anyhow!("ошибка выполнения скрипта {message}"));
            }
            if !self.resolve_response(response)? {
                self.scripts.running = false;
                return Ok(false);
            }
        }
        self.scripts.running = false;
        Ok(true)
    }
}

pub fn read_prompt(prompt: &str) -> Result<String> {
    print!("{prompt}");
    io::stdout().flush()?;
    let mut input = String::new();
    io::stdin().read_line(&mut input)?;
    Ok(input.trim_end().to_string())
}
