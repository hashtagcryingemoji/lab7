package data

import application.convertOrganizationFromTransferData
import application.CollectionManager
import domain.Address
import domain.Coordinates
import domain.Organization
import domain.OrganizationType
import util.PropertiesParser
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

class DBManager(val collectionManager: CollectionManager) {
    val url: String
    val user: String
    val password: String
    var initDate: LocalDate

    init {
        val env = PropertiesParser.getPropertiesFromFile(".env")
        url = env["URL"] ?: throw Error("url for db should be specified in env")
        user = env["USER"] ?: throw Error("username for db should be specified in env")
        password = env["PASSWORD"] ?: throw Error("password for db should be specified in env")
        initDate = LocalDate.now()
    }

    private fun connection(): Connection{
        return DriverManager.getConnection(url, user, password)
    }

    @Synchronized
    fun removeGreater(organization: OrganizationTransferData, userName: String) {
        val statement = "delete from organizations where name > ? and user_id = (select id from users where username = ?) returning id"
        val ids = ArrayList<Int>()

        connection().prepareStatement(statement).use { sqlStatement ->
            sqlStatement.setString(1, organization.name)
            sqlStatement.setString(2, userName)
            sqlStatement.executeQuery().use { rs ->
                while (rs.next()) {
                    ids.add(rs.getInt("id"))
                }

            }
        }

        for (id in ids) {
            collectionManager.removeById(id)
        }
    }

    @Synchronized
    fun removeLower(organization: OrganizationTransferData, userName: String) {
        val statement = "delete from organizations where name < ? and user_id = (select id from users where username = ?) returning id"
        val ids = ArrayList<Int>()

        connection().prepareStatement(statement).use { sqlStatement ->
            sqlStatement.setString(1, organization.name)
            sqlStatement.setString(2, userName)
            sqlStatement.executeQuery().use { rs ->
                while (rs.next()) {
                    ids.add(rs.getInt("id"))
                }

            }
        }

        for (id in ids) {
            collectionManager.removeById(id)
        }
    }

    @Synchronized
    fun removeById(id: Int, userName: String) {

        val isAllowed = checkPermissions(id, userName)

        if (!isAllowed) {
            throw IllegalStateException("Доступ отказан из-за недостающих прав.")
        }

        val statement = "delete from organizations where id = ?"

        connection().prepareStatement(statement).use { sqlStatement ->
            sqlStatement.setInt(1, id)
            sqlStatement.executeUpdate()
        }

        collectionManager.removeById(id)
    }

    @Synchronized
    fun updateById(id: Int, organization: OrganizationTransferData, userName: String) {
        val isAllowed = checkPermissions(id, userName)

        if (!isAllowed) {
            throw IllegalStateException("Доступ отказан из-за недостающих прав.")
        }

        val statement = "update organizations " +
                "set name = ?, x = ?, y = ?, creation_date = ?, " +
                "turnover = ?, full_name = ?, employees_count = ?, " +
                "type = ?, street = ?, zip = ? " +
                "where id = ?"

        connection().prepareStatement(statement).use { sqlStatement ->
            sqlStatement.setString(1, organization.name)
            sqlStatement.setFloat(2, organization.coordinates.x)
            sqlStatement.setFloat(3, organization.coordinates.y)
            sqlStatement.setObject(4, organization.creationDate)
            sqlStatement.setFloat(5, organization.annualTurnover)
            sqlStatement.setString(6, organization.fullName)
            sqlStatement.setInt(7, (organization.employeesCount ?: 0).toInt())
            sqlStatement.setString(8, organization.type.toString())
            sqlStatement.setString(9, organization.officialAddress.street)
            sqlStatement.setString(10, organization.officialAddress.zipCode)
            sqlStatement.setInt(11, id)

            sqlStatement.executeUpdate()
        }

        collectionManager.updateById(id, organization)
    }

    @Synchronized
    fun add(organization: OrganizationTransferData, userName: String) {
        val statement = """
        insert into organizations (
            name, x, y, creation_date, turnover, full_name,
            employees_count, type, street, zip, user_id
        )
        values (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            (select id from users where username = ?)
        )
        returning id
    """.trimIndent()

        val id = connection().prepareStatement(statement).use { sqlStatement ->
            sqlStatement.setString(1, organization.name)
            sqlStatement.setFloat(2, organization.coordinates.x)
            sqlStatement.setFloat(3, organization.coordinates.y)
            sqlStatement.setString(4, organization.creationDate.toString())
            sqlStatement.setFloat(5, organization.annualTurnover)
            sqlStatement.setString(6, organization.fullName)
            sqlStatement.setObject(7, organization.employeesCount?.toInt())
            sqlStatement.setString(8, organization.type.toString())
            sqlStatement.setString(9, organization.officialAddress.street)
            sqlStatement.setString(10, organization.officialAddress.zipCode)

            sqlStatement.setString(11, userName)

            sqlStatement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt("id")
                } else {
                    throw SQLException("Не удалось получить id созданной организации")
                }
            }
        }

        val org = convertOrganizationFromTransferData(id, organization)
        collectionManager.add(org)
    }

    @Synchronized
    fun downloadCollection(): List<Organization> {

        val organizationsList: ArrayList<Organization> = ArrayList()

        val query = """
        SELECT id, name, x, y, creation_date, turnover, full_name, employees_count, type, street, zip 
        FROM organizations
    """.trimIndent()
        connection().prepareStatement(query).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {

                    val org = Organization(
                        rs.getInt("id"),
                        rs.getString("name"),
                        Coordinates(
                            rs.getFloat("x"),
                            rs.getFloat("y"),
                        ),
                        LocalDate.parse(rs.getString("creation_date")),
                        rs.getFloat("turnover"),
                        rs.getString("full_name"),
                        rs.getInt("employees_count").toLong(),
                        OrganizationType.valueOf(rs.getString("type")),
                        Address(
                            rs.getString("street"),
                            rs.getString("zip"),
                        ),
                    )

                    organizationsList.add(org)
                }
            }
        }

        return organizationsList
    }

    @Synchronized
    private fun checkPermissions(id: Int, userName: String): Boolean {
        val statement = """
        SELECT EXISTS (
            SELECT 1 
            FROM organizations o
            JOIN users u ON o.user_id = u.id
            WHERE o.id = ? AND u.username = ?
        )
    """.trimIndent()

        try {
            connection().prepareStatement(statement).use { sqlStatement ->
                sqlStatement.setInt(1, id)
                sqlStatement.setString(2, userName)

                sqlStatement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getBoolean(1)
                    }
                }
            }
        } catch (_: SQLException) {
            println("Ошибка при проверке прав доступа")
        }
        return false
    }

    @Synchronized
    fun register(userName: String, userHashedPassword: String): Result {
        val sql = """
        insert into users (username, password_hash)
        values (?, ?)
    """.trimIndent()

        return try {
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, userName)
                    statement.setString(2, userHashedPassword)

                    statement.executeUpdate()
                }
            }

            Result(
                success = true,
                info = "Пользователь успешно зарегистрирован"
            )
        } catch (e: org.postgresql.util.PSQLException) {
            if (e.sqlState == "23505") {
                Result(
                    success = false,
                    info = "Пользователь с таким именем уже зарегистрирован"
                )
            } else {
                Result(
                    success = false,
                    info = e.message ?: "Ошибка регистрации"
                )
            }
        } catch (e: SQLException) {
            Result(
                success = false,
                info = e.message ?: "Ошибка базы данных"
            )
        }
    }

    fun login(userName: String, userHashedPassword: String): Result {
        val sql = """
        select password_hash
        from users
        where username = ?
    """.trimIndent()

        return try {
            connection().prepareStatement(sql).use { statement ->
                statement.setString(1, userName)

                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return Result(
                            success = false,
                            info = "Пользователь с таким именем не найден"
                        )
                    }

                    val passwordHashFromDb = resultSet.getString("password_hash")

                    if (passwordHashFromDb != userHashedPassword) {
                        return Result(
                            success = false,
                            info = "Неверный пароль"
                        )
                    }

                    return Result(
                        success = true,
                        info = "Вход выполнен успешно"
                    )
                }
            }
        } catch (e: SQLException) {
            Result(
                success = false,
                info = e.message ?: "Ошибка при входе"
            )
        }
    }

    @Synchronized
    fun createSession(
        token: String,
        username: String,
        expiresAt: LocalDateTime
    ) {

        val sql = """
        insert into sessions (
            token,
            username,
            expires_at
        )
        values (?, ?, ?)
    """.trimIndent()

        connection().prepareStatement(sql).use { statement ->

            statement.setString(1, token)

            statement.setString(2, username)

            statement.setTimestamp(
                3,
                Timestamp.valueOf(expiresAt)
            )

            statement.executeUpdate()
        }
    }

    @Synchronized
    fun validateToken(
        token: String
    ): String? {

        val sql = """
        update sessions
        set expires_at = ?
        where token = ?
        and expires_at > now()
        returning username
    """.trimIndent()

        connection().prepareStatement(sql).use { statement ->

            statement.setTimestamp(
                1,
                Timestamp.valueOf(
                    LocalDateTime.now().plusMinutes(15)
                )
            )

            statement.setString(2, token)

            statement.executeQuery().use { rs ->

                if (!rs.next()) {
                    return null
                }

                return rs.getString("username")
            }
        }
    }
}