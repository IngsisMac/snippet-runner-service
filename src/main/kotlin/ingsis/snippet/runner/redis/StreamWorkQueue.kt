package ingsis.snippet.runner.redis

import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Saca el procesamiento de un mensaje del hilo que lo entrega.
 *
 * La librería de cátedra hace `receiveAutoAck(...).subscribe(::onMessage)` sin cambiar de
 * scheduler, así que `onMessage` corre sobre el event loop de Netty de Lettuce. Cualquier bloqueo
 * ahí —JDBC, HTTP o un `XADD` sincrónico— frena la lectura de todos los streams del servicio, y un
 * `XADD` emitido desde ese mismo hilo nunca puede recibir su respuesta: muere por timeout al
 * minuto. Con eso la revalidación masiva avanzaba a un snippet por minuto.
 *
 * La cola es de un solo hilo a propósito: preserva el orden de los mensajes del stream y evita que
 * dos resultados del mismo snippet se pisen al guardar el estado. El hilo es daemon y la librería
 * ya ackeó el mensaje al entregarlo, así que un fallo acá se registra y se descarta, igual que
 * antes de este cambio.
 */
class StreamWorkQueue(
    private val streamName: String,
    private val executor: Executor = singleThread(streamName),
) {
    private val logger = LoggerFactory.getLogger(StreamWorkQueue::class.java)

    @Suppress("TooGenericExceptionCaught")
    fun submit(work: () -> Unit) {
        executor.execute {
            try {
                work()
            } catch (t: Throwable) {
                logger.error("Unhandled error processing a $streamName record", t)
            }
        }
    }

    companion object {
        /** Procesa en el hilo que llama, para que los tests de consumidores sean deterministas. */
        fun direct(streamName: String): StreamWorkQueue = StreamWorkQueue(streamName, Executor { it.run() })

        private fun singleThread(streamName: String): Executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "$streamName-consumer").apply { isDaemon = true }
            }
    }
}
