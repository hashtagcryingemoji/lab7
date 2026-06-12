import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class GatewayToServersChannel(
    private val channel: SocketChannel,
) {
    private val sizeBuffer = ByteBuffer.allocate(4)

    fun read(): Response {

        while (sizeBuffer.hasRemaining()) {

            val bytesRead =
                channel.read(sizeBuffer)

            if (bytesRead == -1) {
                throw IOException(
                    "Соединение с сервером разорвано"
                )
            }
        }

        sizeBuffer.flip()

        val size = sizeBuffer.int

        sizeBuffer.clear()

        val dataBuffer =
            ByteBuffer.allocate(size)

        while (dataBuffer.hasRemaining()) {

            val bytesRead =
                channel.read(dataBuffer)

            if (bytesRead == -1) {
                throw IOException(
                    "Соединение с сервером разорвано"
                )
            }
        }

        dataBuffer.flip()

        val bytes =
            ByteArray(dataBuffer.remaining())

        dataBuffer.get(bytes)

        val json =
            String(bytes, Charsets.UTF_8)


        return Json.decodeFromString<Response>(json)
    }

    fun write(message: Request) {
        val json = Json.encodeToString<Request>(message)
        val bodyBytes = json.toByteArray(Charsets.UTF_8)

        val writeBuffer = ByteBuffer.allocate(4 + bodyBytes.size)
        writeBuffer.putInt(bodyBytes.size)
        writeBuffer.put(bodyBytes)
        writeBuffer.flip()

        while (writeBuffer.hasRemaining()) {
            val written = channel.write(writeBuffer)
            if (written == -1) throw Exception("Disconnected while writing")
        }
    }
}
