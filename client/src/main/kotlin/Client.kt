import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

class Client(val clientContainer: ClientContainer) {
    val io = clientContainer.IO
    val resolver = clientContainer.resolver
    fun run() {

        try {

            val channel =
                SocketChannel.open(
                    InetSocketAddress(
                        clientContainer.hostname,
                        clientContainer.serverPort.toInt()
                    )
                )

            channel.configureBlocking(true)

            val channelIO =
                ChannelIO(channel)

            try {

                io.printBefore("> ")

                val input =
                    io.readLine()

                input?.let {

                    val request =
                        clientContainer
                            .invoker
                            .resolveCommand(
                                input.trim()
                            )
                            ?: return

                    channelIO.write(request)
                }

                val response =
                    channelIO.read()

                resolver.resolve(response)

            } finally {

                channel.close()
            }

        } catch (e: ScriptError) {

            io.printLine(
                "ошибка выполнения скрипта ${e.message}"
            )

        } catch (e: IllegalArgumentException) {

            io.printLine(e.message)
        }
    }
}