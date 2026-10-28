package dev.dux.organizer.spike

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spike: can a small on-device model answer questions about Jarvis data?
 * Skipped unless run with `-e model /data/local/tmp/llm/<file>.litertlm` (optional `-e backend gpu|cpu`).
 * Writes a markdown report to the app's external files dir.
 */
@RunWith(AndroidJUnit4::class)
class LocalLlmBenchmark {

    private val data = """
        Hoy es miércoles 28 de octubre de 2026.
        Tareas pendientes:
        - Pagar el internet (vencida el lunes 26 de octubre, prioridad alta, proyecto Casa)
        - Revisar facturas de clientes (vencida ayer, prioridad alta, proyecto Trabajo)
        - Llamar al dentista para agendar cita (vence hoy, prioridad media)
        - Comprar regalo para mamá (vence el sábado 31 de octubre, prioridad media)
        - Renovar licencia de conducir (vence el 15 de noviembre, prioridad baja)
        - Preparar presentación del lunes (vence el lunes 2 de noviembre, prioridad alta, proyecto Trabajo)
        Lista de compras Súper (por comprar): Leche x2, Café, Jabón para trastes. En el carrito: Tortillas 1 kg, Huevo x30.
        Finanzas de octubre: entró ${'$'}36,000.00, salió ${'$'}7,423.50, te queda ${'$'}28,576.50.
        Presupuesto mensual ${'$'}7,500.00. Comida: gastado ${'$'}4,635.50 de ${'$'}4,000.00. Transporte: ${'$'}600.00 de ${'$'}1,500.00. Servicios: ${'$'}1,949.00 de ${'$'}1,200.00.
    """.trimIndent()

    private val questions = listOf(
        "¿Qué tengo pendiente para hoy?",
        "¿Qué tareas tengo vencidas?",
        "¿Qué es lo más urgente que debo hacer?",
        "¿Qué tengo que hacer esta semana?",
        "¿Qué me falta comprar en el súper?",
        "¿Cuánto dinero me queda este mes?",
        "¿En qué categoría me pasé del presupuesto?",
        "¿Cuántas tareas del proyecto Trabajo tengo?",
        "¿Cuándo vence la licencia de conducir?",
        "Dame un resumen corto de mi día.",
    )

    @Test
    fun answerQuestions() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val modelPath = args.getString("model")
        assumeTrue("no model given", modelPath != null)
        val gpu = args.getString("backend", "gpu") == "gpu"
        val suffix = args.getString("suffix", "")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder("# ${File(modelPath!!).name} · ${if (gpu) "GPU" else "CPU"}\n\n")

        var start = SystemClock.elapsedRealtime()
        Engine(EngineConfig(modelPath = modelPath, backend = if (gpu) Backend.GPU() else Backend.CPU(), cacheDir = context.cacheDir.path)).use { engine ->
            engine.initialize()
            report.append("Carga del modelo: ${SystemClock.elapsedRealtime() - start} ms\n\n")
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    "Eres Jarvis, el asistente de una app de organización personal. Responde en español, breve y claro, " +
                        "usando solo estos datos del usuario. Si no está en los datos, dilo.\n\n$data",
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
            )
            for (q in questions) {
                engine.createConversation(config).use { conversation ->
                    start = SystemClock.elapsedRealtime()
                    var first = -1L
                    val answer = StringBuilder()
                    conversation.sendMessageAsync(q + suffix).collect {
                        if (first < 0) first = SystemClock.elapsedRealtime() - start
                        answer.append(it.toString())
                    }
                    val total = SystemClock.elapsedRealtime() - start
                    report.append("## $q\n_primer texto ${first} ms · total ${total} ms · ${answer.length} caracteres_\n\n${answer.trim()}\n\n")
                    Log.i("LlmBench", "$q -> ${total}ms")
                }
            }
        }
        File(context.getExternalFilesDir(null), "llm-${File(modelPath).nameWithoutExtension}-${if (gpu) "gpu" else "cpu"}.md").writeText(report.toString())
    }
}
