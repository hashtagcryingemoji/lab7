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

class KafkaServerTransport(
    private val container: ServerContainer,
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

    private val consumerGroup =
        env["KAFKA_CONSUMER_GROUP"]
            ?: error("check for KAFKA_CONSUMER_GROUP in .env")

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
                put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup)
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            }
        )

    fun run() {
        consumer.subscribe(listOf(requestTopic))

        println("Server Kafka consumer started: $requestTopic -> $responseTopic")

        while (true) {
            container.IO.process()

            val records =
                consumer.poll(
                    Duration.ofMillis(100)
            )

            var processedSuccessfully = true

            for (record in records) {
                try {
                    handleRecord(record.value())
                } catch (e: Exception) {
                    processedSuccessfully = false
                    e.printStackTrace()
                }
            }

            if (!records.isEmpty && processedSuccessfully) {
                consumer.commitSync()
            }
        }
    }

    private fun handleRecord(
        value: String,
    ) {
        val requestMessage =
            Json.decodeFromString<KafkaRequestMessage>(value)

        println("SERVER GOT KAFKA REQUEST: ${requestMessage.request}")

        val response =
            try {
                container.dispatcher.handleRequest(
                    requestMessage.request
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Response.Error(
                    e.message ?: "Server error"
                )
            }

        println("SERVER KAFKA RESPONSE: $response")

        val responseMessage =
            KafkaResponseMessage(
                correlationId = requestMessage.correlationId,
                response = response,
                createdAt = System.currentTimeMillis(),
            )

        producer.send(
            ProducerRecord(
                responseTopic,
                responseMessage.correlationId,
                Json.encodeToString(responseMessage),
            )
        ).get()
    }
}
