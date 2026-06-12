import application.CommandInvoker
import commands.inner.ExitSignal
import data.DBManager
import org.apache.logging.log4j.kotlin.logger
import thread.RequestResolver
import util.PropertiesParser
import java.util.concurrent.ForkJoinPool

class ServerContainer {
    var commandInvoker = CommandInvoker(this)
    val dispatcher: Dispatcher = Dispatcher(this)
    val dBManager = DBManager()
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
    }

    fun up() {

        try {

            println(
                "Server started as Kafka consumer"
            )

            KafkaServerTransport(this)
                .run()

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
