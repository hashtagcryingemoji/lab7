**Клиент-серверное приложение** с асинхронным обменом сообщениями через Apache Kafka, неблокирующим вводом-выводом на Java NIO, многопоточной обработкой запросов и хранением данных в PostgreSQL.

## Архитектура
Проект состоит из четырёх модулей:

| Модуль | Назначение | Транспорт |
|---|---|---|
| **client** | Интерактивный CLI-клиент | TCP (NIO SocketChannel) |
| **gateway_server** | Балансировщик, проксирующий запросы клиентов в Kafka | TCP (NIO Selector) + Kafka Producer/Consumer |
| **server** | Обработчик команд, работающий с БД | Kafka Consumer/Producer |
| **shared** | Общие DTO, сериализация, утилиты | — |

## Технологии

- **Kotlin 2.0.0** + JVM 17
- **PostgreSQL 42.7.2** (JDBC)
- **Apache Kafka 3.9.0** (топики `imop.requests` / `imop.responses`)
- **Java NIO** (неблокирующие сокеты, Selector)
- **kotlinx-serialization-json 1.6.0**
- **Log4j 2** + Log4j API Kotlin
- **JUnit 5** + MockK
- **Gradle** (Kotlin DSL) + Shadow JAR

## Функциональность

### Команды сервера

| Команда | Описание |
|---|---|
| `add` | Добавить новую организацию |
| `show` | Вывести все организации |
| `info` | Информация о коллекции (размер, дата инициализации) |
| `update <id>` | Обновить организацию по ID (только владелец) |
| `remove_by_id <id>` | Удалить организацию по ID (только владелец) |
| `remove_lower {org}` | Удалить организации, меньшие заданной (только свои) |
| `remove_greater {org}` | Удалить организации, большие заданной (только свои) |
| `count_by_type <type>` | Количество организаций заданного типа |
| `count_less_than_official_address <addr>` | Количество организаций с адресом меньше заданного |
| `sum_of_employees_count` | Сумма всех `employeesCount` |

### Локальные команды клиента

| Команда | Описание |
|---|---|
| `help` | Справка по командам |
| `exit` | Выход |
| `execute_script <file>` | Выполнить скрипт с командами |

## Доменная модель

**Organization**:
- `id` (Int, sequence БД)
- `name` (String, непустой)
- `coordinates` (x: Float ≤ 547, y: Float)
- `creationDate` (LocalDate)
- `annualTurnover` (Float > 0)
- `fullName` (String, unique)
- `employeesCount` (Long?, > 0 если указано)
- `type` (enum): `COMMERCIAL`, `PUBLIC`, `GOVERNMENT`, `PRIVATE_LIMITED_COMPANY`, `OPEN_JOINT_STOCK_COMPANY`
- `officialAddress` (street: String?, zipCode: String?)

## Аутентификация и авторизация

- **Регистрация/вход** — пароль хэшируется алгоритмом MD2 на клиенте
- **Токен сессии** — UUID, хэшированный MD2, с окном жизни 15 минут (скользящее продление)
- **Права доступа** — все пользователи читают коллекцию, изменять объекты может только владелец

## Многопоточность

- **ForkJoinPool** (4 потока) — чтение запросов
- **ForkJoinPool** (4 потока) — отправка ответов
- **ThreadPoolExecutor** (8 потоков, фиксированный) — обработка запросов (`RequestResolver`)
- **@Synchronized** — синхронизация доступа к БД

## Запуск

### Требования

- JDK 17+
- Docker (для Kafka)

### 1. Запуск Kafka

```bash
docker compose up -d
```

### 2. Настройка

Скопировать `.env_example` в `.env` и указать параметры подключения

### 3. Сборка

```bash
./gradlew shadowJar
```

### 4. Запуск сервера

```bash
java -jar server/build/libs/ImopServer-1.0-SNAPSHOT-all.jar
```

### 5. Запуск gateway

```bash
java -jar gateway_server/build/libs/ImopGW-1.0-SNAPSHOT-all.jar
```

### 6. Запуск клиента

```bash
java -jar client/build/libs/ImopClient-1.0-SNAPSHOT-all.jar
```