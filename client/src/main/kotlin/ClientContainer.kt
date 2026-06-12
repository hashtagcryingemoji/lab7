import util.MD2Hasher
import util.PropertiesParser
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

open class ClientContainer {
    val resolver = ViewResolver(this)
    val IO: IOPort = CliManager()
    val clientEnt = Client(this)
    lateinit var socket: SocketChannel
    val scriptManager = ScriptManager()
    val invoker: ClientInvoker = ClientInvoker(this)
    lateinit var channelIO: ChannelIO
    var timeout: Long = 5000
    var serverPort = ""
    var hostname = ""
    lateinit var userToken: String
    init {
        val env = PropertiesParser.getPropertiesFromFile(".env")
        serverPort = env["GW_PORT"] ?: throw Error("server port should be specified in env")
        hostname = env["GW_HOST"] ?: throw Error("hostname should be specified in env")
    }

    fun requestReg(): Pair<String, String> {
        IO.printLine("Введите логин: ")
        IO.printBefore("> ")
        val login: String = IO.readLine() ?: error("login required")
        IO.printLine("Введите пароль: ")
        IO.printBefore("> ")
        val password: String = IO.readLine() ?: error("password required")
        return Pair(MD2Hasher.getMD2Hash(password), login)
    }

    fun up() {
        val address = InetSocketAddress(
            hostname,
            serverPort.toIntOrNull() ?: throw Error("check server port format in env file")
        )
        try {
            val client = SocketChannel.open(address)
            client.configureBlocking(true)
            socket = client
            channelIO = ChannelIO(client)
            IO.printLine("Введите 1, чтобы зарегистрироваться, 2, чтобы войти:")
            IO.printBefore("> ")
            val input = IO.readLine()
            val type = when (input?.toIntOrNull()) {
                1 -> {
                    EnterType.REGISTER
                }
                2 -> {
                    EnterType.LOGIN
                }
                else -> throw Error("invalid input")
            }
            val user = requestReg()
            channelIO.write(Request.HandShake("${user.first} ${user.second}", type))

            val handshakeResponse = channelIO.read()
            resolver.resolve(handshakeResponse)
            //println("получен токен:$userToken")
            timeout = 5000
            while (true) {
                clientEnt.run()
            }
        } catch (_: ExitSignal) {
            return
        } catch (_: RestoreConnectionSignal){
            return up()
        } catch (_: IllegalStateException) {
            return
        } catch (_: ConnectException) {
            IO.printLine("cannot connect to server")
            Thread.sleep(timeout)
            if (timeout < 50000) timeout += 1000
            return up()
        } catch (e: IOException) {
            e.printStackTrace()
            IO.printLine("сервер разорвал подключение")
            return up()
        }
    }
}

fun start(init: ClientContainer.() -> Unit): ClientContainer{
    val container = ClientContainer()
    container.init()
    return container
}