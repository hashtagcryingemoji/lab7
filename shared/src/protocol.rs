use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum EnterType {
    Register,
    Login,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandSyntax {
    pub name: String,
    pub args: Vec<String>,
    pub description: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
pub enum Request {
    HandShake {
        user_hash: String,
        enter_type: EnterType,
    },
    ExecuteCommand {
        user_token: String,
        command_name: String,
        args: Vec<String>,
    },
    Ping,
    HiBalancer {
        host: String,
        port: u16,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
pub enum Response {
    HandShake {
        commands: Vec<CommandSyntax>,
        token: String,
    },
    Info {
        message: String,
    },
    Error {
        message: String,
    },
    ResetTokenPlease,
    Shutdown,
    Pong,
}

pub fn command_syntax(name: &str, args: &[&str], description: &str) -> CommandSyntax {
    CommandSyntax {
        name: name.to_string(),
        args: args.iter().map(|arg| arg.to_string()).collect(),
        description: description.to_string(),
    }
}
