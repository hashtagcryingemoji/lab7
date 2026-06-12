use anyhow::{anyhow, Context, Result};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use std::cmp::Ordering;
use std::fmt::{self, Display};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Coordinates {
    pub x: f32,
    pub y: f32,
}

impl Coordinates {
    pub fn new(x: f32, y: f32) -> Result<Self> {
        if x > 547.0 {
            return Err(anyhow!("Поле x должно быть меньше 547"));
        }
        Ok(Self { x, y })
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Eq, PartialEq)]
pub struct Address {
    pub street: Option<String>,
    pub zip_code: Option<String>,
}

impl Ord for Address {
    fn cmp(&self, other: &Self) -> Ordering {
        let zip = self
            .zip_code
            .as_deref()
            .map_or("", |zip| zip)
            .cmp(other.zip_code.as_deref().map_or("", |zip| zip));
        if zip == Ordering::Equal {
            self.street
                .as_deref()
                .map_or("", |street| street)
                .cmp(other.street.as_deref().map_or("", |street| street))
        } else {
            zip
        }
    }
}

impl PartialOrd for Address {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Display for Address {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Address(street={}, zipCode={})",
            self.street.as_deref().map_or("null", |street| street),
            self.zip_code.as_deref().map_or("null", |zip| zip)
        )
    }
}

#[derive(Debug, Copy, Clone, Serialize, Deserialize, Eq, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OrganizationType {
    Commercial,
    Public,
    Government,
    PrivateLimitedCompany,
    OpenJointStockCompany,
}

impl OrganizationType {
    pub fn parse(input: &str) -> Result<Self> {
        match input.trim().to_ascii_lowercase().replace('_', " ").as_str() {
            "commercial" => Ok(Self::Commercial),
            "public" => Ok(Self::Public),
            "government" => Ok(Self::Government),
            "private limited company" => Ok(Self::PrivateLimitedCompany),
            "open joint stock company" => Ok(Self::OpenJointStockCompany),
            _ => Err(anyhow!("Введён некоректный формат типа организации")),
        }
    }
}

impl Display for OrganizationType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let value = match self {
            Self::Commercial => "COMMERCIAL",
            Self::Public => "PUBLIC",
            Self::Government => "GOVERNMENT",
            Self::PrivateLimitedCompany => "PRIVATE_LIMITED_COMPANY",
            Self::OpenJointStockCompany => "OPEN_JOINT_STOCK_COMPANY",
        };
        f.write_str(value)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OrganizationData {
    pub name: String,
    pub coordinates: Coordinates,
    pub creation_date: NaiveDate,
    pub annual_turnover: f32,
    pub full_name: String,
    pub employees_count: Option<i64>,
    pub organization_type: OrganizationType,
    pub official_address: Address,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Organization {
    pub id: i32,
    pub data: OrganizationData,
}

impl Organization {
    pub fn new(id: i32, data: OrganizationData) -> Result<Self> {
        if data.name.is_empty() {
            return Err(anyhow!("Строка не может быть пустой"));
        }
        if data.annual_turnover <= 0.0 {
            return Err(anyhow!("Значение поля annualTurnover должно быть больше 0"));
        }
        if matches!(data.employees_count, Some(count) if count <= 0) {
            return Err(anyhow!("Значение поля employeesCount должно быть больше 0"));
        }
        Ok(Self { id, data })
    }
}

impl Display for Organization {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Организация '{}': Id: {}, тип: {}, адрес: {}, координаты: Coordinates(x={}, y={}), дата создания: {}, годичная выручка: {}, полное название: {}, количество сотрудников: {},",
            self.data.name,
            self.id,
            self.data.organization_type,
            self.data.official_address,
            self.data.coordinates.x,
            self.data.coordinates.y,
            self.data.creation_date,
            self.data.annual_turnover,
            self.data.full_name,
            self.data
                .employees_count
                .map(|v| v.to_string())
                .map_or_else(|| "null".to_string(), |value| value)
        )
    }
}

pub fn build_organization(args: &[String]) -> Result<OrganizationData> {
    if args.len() < 9 {
        return Err(anyhow!(
            "Недостаточно аргументов: ожидалось 9, получено {}",
            args.len()
        ));
    }
    let name = args[0].trim().to_string();
    let x = args[1]
        .trim()
        .parse::<f32>()
        .context("Некорректное значение X")?;
    let y = args[2]
        .trim()
        .parse::<f32>()
        .context("Некорректное значение Y")?;
    let annual_turnover = args[3]
        .trim()
        .parse::<f32>()
        .context("Некорректная годичная выручка")?;
    let full_name = args[4].trim().to_string();
    let employees_count = args[5].trim().parse::<i64>().ok();
    let street = args[6].trim().to_string();
    let zip_code = args[7].trim().to_string();
    let organization_type = OrganizationType::parse(&args[8])?;

    let data = OrganizationData {
        name,
        coordinates: Coordinates::new(x, y)?,
        creation_date: chrono::Local::now().date_naive(),
        annual_turnover,
        full_name,
        employees_count,
        organization_type,
        official_address: Address {
            street: Some(street),
            zip_code: Some(zip_code),
        },
    };
    Organization::new(0, data.clone())?;
    Ok(data)
}
