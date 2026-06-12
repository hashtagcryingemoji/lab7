use anyhow::{anyhow, Result};
use std::collections::{HashSet, VecDeque};
use std::fs;

pub struct ScriptManager {
    buffer: VecDeque<Vec<String>>,
    pub running: bool,
    active: HashSet<String>,
}

impl ScriptManager {
    pub fn new() -> Self {
        Self {
            buffer: VecDeque::new(),
            running: false,
            active: HashSet::new(),
        }
    }

    pub fn add(&mut self, path: String) -> Result<()> {
        if self.active.contains(&path) {
            self.panic();
            return Err(anyhow!("рекурсия"));
        }
        let lines = fs::read_to_string(&path)?
            .lines()
            .rev()
            .map(|line| line.to_string())
            .collect();
        self.active.insert(path);
        self.buffer.push_back(lines);
        Ok(())
    }

    pub fn get_line(&mut self) -> Option<String> {
        let script = self.buffer.back_mut()?;
        let line = script.pop();
        if script.is_empty() {
            self.buffer.pop_back();
        }
        line
    }

    pub fn panic(&mut self) {
        self.buffer.clear();
        self.active.clear();
        self.running = false;
    }
}
