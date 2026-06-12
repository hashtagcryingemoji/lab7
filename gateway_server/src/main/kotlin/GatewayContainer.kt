import org.apache.logging.log4j.Level
import org.apache.logging.log4j.kotlin.logger
import util.PropertiesParser
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

open class GatewayContainer {

    private val balancer = Balancer()

    private val logger = logger()

    private var serverPort = ""

    private var hostname = ""

    init {

        val env =
            PropertiesParser.getPropertiesFromFile(".env")

        serverPort =
            env["GW_PORT"]
                ?: error("check for GW_PORT in .env")

        hostname =
            env["GW_HOST"]
                ?: error("check for GW_HOST in .env")
    }

    fun up() {

        try {

            val selector =
                Selector.open()

            val serverSocket =
                ServerSocketChannel.open()

            serverSocket.configureBlocking(false)

            serverSocket.bind(
                InetSocketAddress(
                    hostname,
                    serverPort.toInt()
                )
            )

            serverSocket.register(
                selector,
                SelectionKey.OP_ACCEPT
            )

            println(
                "Gateway started at $hostname:$serverPort"
            )

            while (true) {

                processInput()

                selector.select()

                val iterator =
                    selector.selectedKeys().iterator()

                while (iterator.hasNext()) {

                    val key = iterator.next()

                    iterator.remove()

                    if (!key.isValid) {
                        continue
                    }

                    try {

                        when {

                            key.isAcceptable -> {
                                onAccept(
                                    selector,
                                    serverSocket
                                )
                            }

                            key.isReadable -> {
                                onReadable(key)
                            }

                            key.isWritable -> {
                                onWritable(key)
                            }
                        }

                    } catch (e: Exception) {

                        e.printStackTrace()

                        closeKey(key)
                    }
                }
            }

        } catch (_: ExitSignal) {

            println("Gateway stopped")
        }
    }

    private fun onAccept(
        selector: Selector,
        serverSocket: ServerSocketChannel,
    ) {

        val client =
            serverSocket.accept()
                ?: return

        client.configureBlocking(false)

        val io =
            ClientsToGatewayChannel(client)

        client.register(
            selector,
            SelectionKey.OP_READ,
            io
        )

        println(
            "Client connected: ${client.remoteAddress}"
        )
    }

    private fun onReadable(
        key: SelectionKey,
    ) {
        val io =
            key.attachment()
                    as ClientsToGatewayChannel

        val request =
            io.readRequestIfReady()
                ?: return

        val response =
            balancer.handle(request)

        io.prepareResponse(response)

        key.interestOps(
            SelectionKey.OP_WRITE
        )
    }

    private fun onWritable(
        key: SelectionKey,
    ) {

        val io =
            key.attachment()
                    as ClientsToGatewayChannel

        val done =
            io.writeResponseIfReady()

        if (!done) {
            return
        }

        closeKey(key)
    }

    private fun closeKey(
        key: SelectionKey,
    ) {

        try {

            key.channel().close()

        } catch (_: Exception) {
        }

        try {

            key.cancel()

        } catch (_: Exception) {
        }
    }

    private fun processInput() {

        val input =
            if (System.`in`.available() > 0) {
                readlnOrNull()
            } else {
                null
            }

        if (input.isNullOrBlank()) {
            return
        }

        try {

            val tokens =
                input.split(" ")

            val name =
                tokens.first()

            val args =
                tokens.drop(1)

            logger.log(
                Level.INFO,
                "$name $args"
            )

            if (name == "shutdown") {
                throw ExitSignal()
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }
}