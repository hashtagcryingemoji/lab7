import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import util.PropertiesParser
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class KafkaGatewayTransport(
    private val timeoutMillis: Long = 10_000,
) {
    private val env =
        PropertiesParser.getPropertiesFromFile(".env")

    private val bootstrapServers =
        env["KAFKA_BOOTSTRAP_SERVERS"]
            ?: error("check for KAFKA_BOOTSTRAP_SERVERS in .env")

    private val requestTopic =
        env["KAFKA_REQUEST_TOPIC"]
            ?: error("check for KAFKA_REQUEST_TOPIC in .env")

    private val responseTopic =
        env["KAFKA_RESPONSE_TOPIC"]
            ?: error("check for KAFKA_RESPONSE_TOPIC in .env")

    private val pendingResponses =
        ConcurrentHashMap<String, CompletableFuture<Response>>()

    private val producer =
        KafkaProducer<String, String>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.ACKS_CONFIG, "all")
            }
        )

    private val consumer =
        KafkaConsumer<String, String>(
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.GROUP_ID_CONFIG, "imop-gateway-${UUID.randomUUID()}")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            }
        )

    init {
        consumer.subscribe(listOf(responseTopic))

        Thread(
            { consumeResponses() },
            "kafka-gateway-response-consumer"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun send(
        request: Request,
    ): Response {
        val correlationId =
            UUID.randomUUID().toString()

        val future =
            CompletableFuture<Response>()

        pendingResponses[correlationId] = future

        return try {
            val message =
                KafkaRequestMessage(
                    correlationId = correlationId,
                    request = request,
                    createdAt = System.currentTimeMillis(),
                )

            val json =
                Json.encodeToString(message)

            producer.send(
                ProducerRecord(
                    requestTopic,
                    correlationId,
                    json,
                )
            ).get()

            future.get(
                timeoutMillis,
                TimeUnit.MILLISECONDS
            )

        } catch (e: Exception) {

            e.printStackTrace()

            Response.Error("Server timeout")

        } finally {

            pendingResponses.remove(correlationId)
        }
    }

    private fun consumeResponses() {
        while (true) {
            try {
                val records =
                    consumer.poll(
                        Duration.ofMillis(100)
                    )

                for (record in records) {
                    val message =
                        Json.decodeFromString<KafkaResponseMessage>(
                            record.value()
                        )

                    pendingResponses
                        .remove(message.correlationId)
                        ?.complete(message.response)
                }

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }
}
