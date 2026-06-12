class Balancer {

    private val kafkaTransport =
        KafkaGatewayTransport()

    fun handle(
        request: Request
    ): Response {

        return when (request) {

            is Request.HiBalancer -> {

                println(
                    "Server registration ignored after Kafka transport migration: ${request.host}:${request.port}"
                )

                Response.Pong
            }

            else -> {
                proxyRequest(request)
            }
        }
    }

    private fun proxyRequest(
        request: Request
    ): Response {

        return kafkaTransport.send(request)
    }
}
