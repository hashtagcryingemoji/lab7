import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class ClientsToGatewayChannel(
    private val channel: SocketChannel,
) {
    private val headerBuffer: ByteBuffer = ByteBuffer.allocate(4)
    private var bodyBuffer: ByteBuffer? = null
    private var writeBuffer: ByteBuffer? = null

    fun readRequestIfReady(): Request? {

        if (bodyBuffer == null) {

            while (headerBuffer.hasRemaining()) {

                val readHeader =
                    channel.read(headerBuffer)

                if (readHeader == -1) {
                    throw EOFException("Channel closed")
                }

                if (readHeader == 0) {
                    return null
                }
            }

            headerBuffer.flip()

            val size = headerBuffer.int

            headerBuffer.clear()

            if (size < 0) {
                throw IllegalStateException(
                    "Negative frame size"
                )
            }

            bodyBuffer =
                ByteBuffer.allocate(size)
        }

        val body =
            bodyBuffer ?: return null

        while (body.hasRemaining()) {

            val readBody =
                channel.read(body)

            if (readBody == -1) {
                throw EOFException("Channel closed")
            }

            if (readBody == 0) {
                return null
            }
        }

        body.flip()

        val bytes =
            ByteArray(body.remaining())

        body.get(bytes)

        bodyBuffer = null

        val json =
            String(bytes, Charsets.UTF_8)

        return Json.decodeFromString<Request>(json)
    }

    fun prepareResponse(
        response: Response
    ) {

        val json =
            Json.encodeToString(response)

        val bytes =
            json.toByteArray(Charsets.UTF_8)

        println(
            "WRITE RESPONSE JSON: $json"
        )

        writeBuffer =
            ByteBuffer.allocate(
                4 + bytes.size
            ).apply {

                putInt(bytes.size)

                put(bytes)

                flip()
            }
    }

    fun writeResponseIfReady(): Boolean {

        val buffer =
            writeBuffer ?: return true

        val written =
            channel.write(buffer)

        if (written == -1) {
            throw EOFException("closed")
        }

        if (buffer.hasRemaining()) {
            return false
        }

        writeBuffer = null

        return true
    }
}