use crate::collection::CollectionManager;
use crate::db::Database;
use anyhow::{anyhow, Context, Result};
use lab7_shared::{
    build_organization, command_syntax, Address, CommandSyntax, EnterType, OrganizationType,
    Request, Response,
};
use std::cmp::Ordering;
use std::sync::Arc;
use tokio::sync::RwLock;
use uuid::Uuid;

pub struct App {
    pub db: Database,
    pub collection: Arc<RwLock<CollectionManager>>,
}

impl App {
    pub fn commands() -> Vec<CommandSyntax> {
        vec![
            command_syntax("show", &[], "Выводит список всех организаций"),
            command_syntax("add", &ORG_ARGS, "Добавляет организацию в коллекцию"),
            command_syntax(
                "count_by_type",
                &["Type"],
                "Подсчитывает количество организаций заданного типа",
            ),
            command_syntax("info", &[], "Выводит информацию о коллекции"),
            command_syntax(
                "sum_of_employees_count",
                &[],
                "Возвращает количество работяг во всей коллекции",
            ),
            command_syntax(
                "count_less_than_official_address",
                &["Street", "Zip"],
                "Подсчитывает количество организаций чей адрес меньше заданного",
            ),
            command_syntax(
                "remove_lower",
                &ORG_ARGS,
                "Удаляет из коллекции все элементы, меньше чем",
            ),
            command_syntax(
                "remove_greater",
                &ORG_ARGS,
                "Удаляет из коллекции все элементы, превышающие заданный",
            ),
            command_syntax(
                "remove_by_id",
                &["Id"],
                "Удаляет из коллекции элемент по Id",
            ),
            command_syntax(
                "update",
                &UPDATE_ARGS,
                "Обновляет элемент в коллекции по заданному id",
            ),
        ]
    }

    pub async fn handle(&self, request: Request) -> Response {
        match request {
            Request::Ping => Response::Pong,
            Request::HandShake {
                user_hash,
                enter_type,
            } => self.handshake(&user_hash, enter_type).await,
            Request::ExecuteCommand {
                user_token,
                command_name,
                args,
            } => {
                let user = match self.db.validate_token(&user_token).await {
                    Ok(Some(user)) => user,
                    Ok(None) => return Response::ResetTokenPlease,
                    Err(err) => {
                        return Response::Error {
                            message: err.to_string(),
                        }
                    }
                };
                match self.execute(&command_name, &args, &user).await {
                    Ok(message) => Response::Info { message },
                    Err(err) => Response::Error {
                        message: err.to_string(),
                    },
                }
            }
            Request::HiBalancer { .. } => Response::Error {
                message: "Server cannot register balancer nodes".to_string(),
            },
        }
    }

    async fn handshake(&self, user_hash: &str, enter_type: EnterType) -> Response {
        let Some((password_hash, name)) = user_hash.split_once(' ') else {
            return Response::Error {
                message: "Handshake error".to_string(),
            };
        };
        let result = match enter_type {
            EnterType::Login => self.db.login(name, password_hash).await.map(|_| ()),
            EnterType::Register => self.db.register(name, password_hash).await.map(|_| ()),
        };
        if result.is_err() {
            return Response::Error {
                message: "Данное имя занято или введен неверный пароль.".to_string(),
            };
        }
        let token = lab7_shared::md2_hash(&Uuid::new_v4().to_string());
        if let Err(err) = self.db.create_session(&token, name).await {
            return Response::Error {
                message: err.to_string(),
            };
        }
        Response::HandShake {
            commands: Self::commands(),
            token,
        }
    }

    async fn execute(&self, name: &str, args: &[String], user: &str) -> Result<String> {
        match name {
            "show" => self.show().await,
            "add" => {
                self.db.add(build_organization(args)?, user).await?;
                Ok("Организация успешно добавлена".to_string())
            }
            "count_by_type" => self.count_by_type(args).await,
            "info" => self.info().await,
            "sum_of_employees_count" => self.sum_of_employees_count().await,
            "count_less_than_official_address" => self.count_less_than_official_address(args).await,
            "remove_lower" => {
                let data = build_organization(args)?;
                let count = self
                    .db
                    .remove_by_name_cmp(&data, user, Ordering::Less)
                    .await?;
                Ok(format!("Из коллекции удалено {count} элементов"))
            }
            "remove_greater" => {
                let data = build_organization(args)?;
                let count = self
                    .db
                    .remove_by_name_cmp(&data, user, Ordering::Greater)
                    .await?;
                Ok(format!("Из коллекции удалено {count} элементов"))
            }
            "remove_by_id" => {
                let id = args
                    .first()
                    .context("Id required")?
                    .parse::<i32>()
                    .context("Введенный аргумент не является числом.")?;
                self.db.remove_by_id(id, user).await?;
                Ok(format!("Элемент с Id {id} удален."))
            }
            "update" => {
                let id = args.first().context("Id required")?.parse::<i32>()?;
                self.db
                    .update_by_id(id, build_organization(&args[1..])?, user)
                    .await?;
                Ok("Организация успешно обновлена.".to_string())
            }
            _ => Err(anyhow!("Команда {name} не найдена")),
        }
    }

    async fn show(&self) -> Result<String> {
        let collection = self.collection.read().await.sorted();
        if collection.is_empty() {
            Ok("Вы еще не успели насоздавать шедевров...".to_string())
        } else {
            Ok(collection
                .iter()
                .map(ToString::to_string)
                .collect::<Vec<_>>()
                .join("\n"))
        }
    }

    async fn count_by_type(&self, args: &[String]) -> Result<String> {
        let org_type = OrganizationType::parse(args.first().map_or("", String::as_str))?;
        let count = self
            .collection
            .read()
            .await
            .organizations
            .iter()
            .filter(|org| org.data.organization_type == org_type)
            .count();
        Ok(count.to_string())
    }

    async fn info(&self) -> Result<String> {
        let collection = self.collection.read().await;
        if collection.organizations.is_empty() {
            return Ok("коллекция пуста:(".to_string());
        }

        let mut message = format!(
            "Количество элементов в коллекции: {}\nдата создания шедевра - {}\nОрганизации в коллекции:\n",
            collection.organizations.len(),
            collection
                .init_date
                .map(|date| date.to_string())
                .map_or_else(|| "Коллекция еще не создана".to_string(), |date| date)
        );
        for org in collection.sorted() {
            message.push_str(&format!("{} с id номер {}\n", org.data.full_name, org.id));
        }
        Ok(message)
    }

    async fn sum_of_employees_count(&self) -> Result<String> {
        let sum: i64 = self
            .collection
            .read()
            .await
            .organizations
            .iter()
            .map(|org| org.data.employees_count.map_or(0, |count| count))
            .sum();
        Ok(format!("Общее количество работяг в коллекции: {sum}"))
    }

    async fn count_less_than_official_address(&self, args: &[String]) -> Result<String> {
        let address = Address {
            street: args.first().cloned(),
            zip_code: args.get(1).cloned(),
        };
        let count = self
            .collection
            .read()
            .await
            .organizations
            .iter()
            .filter(|org| org.data.official_address < address)
            .count();
        Ok(format!("Организаций с меньшим адресом: {count}"))
    }
}

const ORG_ARGS: [&str; 9] = [
    "Name",
    "X",
    "Y",
    "Annual turnover",
    "Full name (unique)",
    "Employee count",
    "Street",
    "Zip code",
    "Type",
];

const UPDATE_ARGS: [&str; 10] = [
    "Id",
    "Name",
    "X",
    "Y",
    "Annual turnover",
    "Full name (unique)",
    "Employee count",
    "Street",
    "Zip code",
    "Type",
];
