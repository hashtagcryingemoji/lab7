import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

data class Node(
    val host: String,
    val port: Int,
)

class Balancer {

    private val availableServers =
        mutableListOf<Node>()

    private var counter = 0

    fun handle(
        request: Request
    ): Response {

        return when (request) {

            is Request.HiBalancer -> {

                registerServer(
                    request.host,
                    request.port
                )

                Response.Pong
            }

            else -> {
                proxyRequest(request)
            }
        }
    }

    private fun registerServer(
        host: String,
        port: Int
    ) {

        val exists = availableServers.any {
                it.host == host &&
                        it.port == port
            }

        if (!exists) {

            availableServers.add(Node(host, port))

            println("Registered server $host:$port")
        }
    }

    private fun proxyRequest(
        request: Request
    ): Response {

        if (availableServers.isEmpty()) {
            return Response.Error("Нет серверов")
        }

        val node =
            availableServers[counter % availableServers.size]

        counter++

        return try {

            SocketChannel.open().use { socket ->

                socket.connect(
                    InetSocketAddress(
                        node.host,
                        node.port
                    )
                )

                val io = GatewayToServersChannel(socket)

                io.write(request)

                io.read()
            }

        } catch (e: Exception) {

            e.printStackTrace()

            Response.Error("Server unavailable")
        }
    }
}
