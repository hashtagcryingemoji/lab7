use anyhow::{anyhow, Result};
use chrono::{NaiveDate, Utc};
use lab7_shared::{Organization, OrganizationData};

#[derive(Default)]
pub struct CollectionManager {
    pub organizations: Vec<Organization>,
    pub init_date: Option<NaiveDate>,
}

impl CollectionManager {
    pub fn upload(&mut self, collection: Vec<Organization>) {
        self.init_date = if collection.is_empty() {
            None
        } else {
            Some(Utc::now().date_naive())
        };
        self.organizations = collection;
    }

    pub fn add(&mut self, org: Organization) -> Result<()> {
        if self
            .organizations
            .iter()
            .any(|item| item.data.full_name == org.data.full_name)
        {
            return Err(anyhow!("Полное имя организации не уникально."));
        }
        if self.organizations.is_empty() {
            self.init_date = Some(Utc::now().date_naive());
        }
        self.organizations.push(org);
        Ok(())
    }

    pub fn update(&mut self, id: i32, data: OrganizationData) {
        if let Some(org) = self.organizations.iter_mut().find(|org| org.id == id) {
            org.data = data;
        }
    }

    pub fn remove(&mut self, id: i32) {
        self.organizations.retain(|org| org.id != id);
    }

    pub fn sorted(&self) -> Vec<Organization> {
        let mut items = self.organizations.clone();
        items.sort_by(|a, b| a.data.name.cmp(&b.data.name));
        items
    }
}
