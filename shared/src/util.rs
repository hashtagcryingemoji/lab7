use anyhow::{Context, Result};
use md2::{Digest, Md2};
use serde::{de::DeserializeOwned, Serialize};
use std::collections::HashMap;
use std::fs;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

pub fn read_env(path: &str) -> Result<HashMap<String, String>> {
    let text = fs::read_to_string(path).with_context(|| "env file should be specified")?;
    Ok(text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .filter_map(|line| line.split_once('='))
        .map(|(key, value)| (key.trim().to_string(), value.trim().to_string()))
        .collect())
}

pub fn md2_hash(input: &str) -> String {
    let mut hasher = Md2::new();
    hasher.update(input.as_bytes());
    hasher
        .finalize()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

pub async fn write_frame<T, W>(stream: &mut W, message: &T) -> Result<()>
where
    T: Serialize,
    W: AsyncWrite + Unpin,
{
    let bytes = serde_json::to_vec(message)?;
    let len = u32::try_from(bytes.len()).context("Frame is too large")?;
    stream.write_all(&len.to_be_bytes()).await?;
    stream.write_all(&bytes).await?;
    stream.flush().await?;
    Ok(())
}

pub async fn read_frame<T, R>(stream: &mut R) -> Result<T>
where
    T: DeserializeOwned,
    R: AsyncRead + Unpin,
{
    let mut header = [0_u8; 4];
    stream.read_exact(&mut header).await?;
    let len = u32::from_be_bytes(header) as usize;
    let mut body = vec![0_u8; len];
    stream.read_exact(&mut body).await?;
    Ok(serde_json::from_slice(&body)?)
}
