import application.CommandInvoker
import data.DBManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.kotlin.logger
import thread.RequestResolver
import util.PropertiesParser
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ForkJoinPool
import commands.inner.ExitSignal

class ServerContainer {
    var commandInvoker = CommandInvoker(this)
    val dispatcher: Dispatcher = Dispatcher(this)
    val collectionManager = application.CollectionManager()
    val dBManager = DBManager(collectionManager)
    val IO = ServerCli(this)
    val logger = logger()
    var serverPort = ""
    var hostname = ""

    val readPool = ForkJoinPool(4)
    val writePool = ForkJoinPool(4)
    val requestResolver = RequestResolver()

    init {
        val env = PropertiesParser.getPropertiesFromFile(".env")
        serverPort = env["SERVER_PORT"] ?: throw Error("server port should be specified in env")
        hostname = env["HOST_NAME"] ?: throw Error("hostname should be specified in env")
        collectionManager.uploadCollection(dBManager.downloadCollection())

        val balancerPort = env["GW_PORT"] ?: throw Error("hostname should be specified in env")
        val balancerHost = env["GW_HOST"] ?: throw Error("hostname should be specified in env")

        val address = InetSocketAddress(
            balancerHost,
            balancerPort.toIntOrNull() ?: error("no")
        )
        SocketChannel.open(address).use { socketChannel ->
            val json = Json.encodeToString<Request>(
                Request.HiBalancer(
                    hostname,
                    serverPort.toIntOrNull() ?: throw Error("check for server port format in env file")
                )
            )
            val bodyBytes = json.toByteArray(Charsets.UTF_8)

            val writeBuffer = ByteBuffer.allocate(4 + bodyBytes.size)
            writeBuffer.putInt(bodyBytes.size)
            writeBuffer.put(bodyBytes)
            writeBuffer.flip()

            while (writeBuffer.hasRemaining()) {
                val written = socketChannel.write(writeBuffer)
                if (written == -1) throw Exception("Disconnected while writing")
            }


        }
    }

    fun up() {

        try {

            val serverSocket =
                ServerSocketChannel.open()

            serverSocket.bind(
                InetSocketAddress(
                    hostname,
                    serverPort.toIntOrNull()
                        ?: error("bad port")
                )
            )

            println(
                "Server started at $hostname:$serverPort"
            )

            while (true) {

                IO.process()

                val client =
                    serverSocket.accept()

                println(
                    "Client connected: ${client.remoteAddress}"
                )

                readPool.execute {

                    val state =
                        ClientState(client)

                    try {

                        val request =
                            state.read() ?: error("empty request")

                        println(
                            "SERVER GOT REQUEST: $request"
                        )

                        IO.write(
                            "$request from ${client.remoteAddress}"
                        )

                        val response =
                            dispatcher.handleRequest(request)

                        println(
                            "SERVER RESPONSE: $response"
                        )

                        state.write(response)

                    } catch (e: Exception) {

                        e.printStackTrace()

                    } finally {

                        try {
                            client.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }

        } catch (_: ExitSignal) {

            requestResolver.shutdown()

            writePool.shutdown()

            readPool.shutdown()

            println("Сервер выключается.")
        }
    }
}

fun serverContainer(container: ServerContainer.() -> Unit): ServerContainer {
    val serv = ServerContainer()
    serv.container()
    return serv
}