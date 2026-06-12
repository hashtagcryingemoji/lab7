import kotlinx.serialization.Serializable

@Serializable
data class KafkaRequestMessage(
    val correlationId: String,
    val request: Request,
    val createdAt: Long,
)

@Serializable
data class KafkaResponseMessage(
    val correlationId: String,
    val response: Response,
    val createdAt: Long,
)
