import util.MD2Hasher
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID

class Dispatcher(
    val container: ServerContainer,
) {
    val invoker = container.commandInvoker

    fun handleRequest(request: Request): Response {
        when (request) {
            is Request.ExecuteCommand -> try {
                val user =
                    container.dBManager.validateToken(request.userToken) ?: return Response.ResetTokenPlease
                val result = invoker.handleInput(request, user)
                return if (result.success) Response.Info(result.info)
                else Response.Error(result.info)
            } catch (_: TokenExpiredException) {
                return Response.ResetTokenPlease
            } catch (_: ExitSignal) {
                return Response.Shutdown
            } catch (_: SQLException) {
                println("не удалось взаимодействовать с базой данных")
            } catch (e: Exception) {
                e.printStackTrace()
                val rpc = Response.Error(e.message ?: "No error message specified")

                return rpc
            }

            is Request.HandShake -> try {

                val dbManager =
                    container.dBManager

                val (passwordHashed, name) =
                    request.userHash.split(" ")

                val success =
                    when (request.enterType) {

                        EnterType.LOGIN -> {

                            dbManager.login(
                                name,
                                passwordHashed
                            ).success
                        }

                        EnterType.REGISTER -> {

                            dbManager.register(
                                name,
                                passwordHashed
                            ).success
                        }
                    }

                if (!success) {

                    return Response.Error(
                        "Данное имя занято или введен неверный пароль."
                    )
                }

                val token = MD2Hasher.getMD2Hash(UUID.randomUUID().toString())

                dbManager.createSession(
                    token,
                    name,
                    LocalDateTime.now().plusMinutes(15)
                )

                return Response.HandShake(
                    invoker.getCommands(),
                    token
                )

            } catch (e: Exception) {

                e.printStackTrace()

                return Response.Error(
                    e.message ?: "Handshake error"
                )
            }

            is Request.Ping -> {
                return Response.Pong
            }

            else -> {}
        }

        return Response.Error("Something went wrong")
    }
}