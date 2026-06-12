import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class ServerChannelIO(
    private val channel: SocketChannel,
) {
    private var size = -1
    private val sizeBuffer = ByteBuffer.allocate(4)
    private lateinit var dataBuffer: ByteBuffer

    fun read(): Request? {
        if (size == -1) {
            val bytesRead = channel.read(sizeBuffer)
            //println(bytesRead)
            if (bytesRead == -1) throw Exception("Channel closed")
            if (sizeBuffer.hasRemaining()) return null

            sizeBuffer.flip()
            size = sizeBuffer.int
            //println(size)
            sizeBuffer.clear()

            dataBuffer = ByteBuffer.allocate(size)
        }

        val bytesReadData = channel.read(dataBuffer)
        if (bytesReadData == -1) throw Exception("Channel closed")

        if (dataBuffer.hasRemaining()) return null

        val json = String(dataBuffer.array(), Charsets.UTF_8)
        val rpc = Json.decodeFromString<Request>(json)

        size = -1

        return rpc
    }

    fun write(message: Response) {
        val json = Json.encodeToString<Response>(message)
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
