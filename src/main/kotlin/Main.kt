import com.ashampoo.kim.Kim
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.model.ImageSize
import com.ashampoo.kim.readMetadata
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.lens.*
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries
import java.io.File
import java.math.RoundingMode

data class Name(val value: String)

fun main(args: Array<String>) {
    val nameField = MultipartFormField.string().map(::Name, Name::value).required("name")
    val imageFile = MultipartFormFile.optional("image")

    val ratioToSize: Map<Double, String> = mapOf(
        3.2.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "320_100",
        6.0.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "300_50",
        1.0.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "250_250",
        0.6666667.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "320_480",
        6.408.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "320_50",
        0.50.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "300_600",
        0.26666668.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "160_600",
        3.88.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "970_250",
        8.088889.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble() to "728_90"
    )
    val fp = args[0]
    println("fp: $fp")

    val p = Paths.get(fp)
    println("p: $p")
    val files = p.listDirectoryEntries("*{png,jpg,jpeg}")

    val metadata = files
        .map { f ->
            Triple<String, ImageMetadata?, File>(
                f.fileName.toString(),
                Kim.readMetadata(f.absolutePathString()),
                f.toFile()
            )
        }
        .map { m ->
            image(
                m.first, m.second?.imageSize
                    .let { i: ImageSize? ->
                        val arr = i.toString().split(" x ")
                        val a = arr[0].toDouble()
                        val b = arr[1].toDouble()
                        val z = a / b
                        val s =
                            ratioToSize.getOrDefault(z.toBigDecimal().setScale(1, RoundingMode.FLOOR).toDouble(), "ERR")
                        s
                    }, m.third
            )
        }
    val strictFormBody =
        Body.multipartForm(Validator.Strict, nameField, imageFile, diskThreshold = 5).toLens()

    val rqs = metadata.map { img ->
        val body = MultipartForm().with(
            nameField of Name(img.ratio.orEmpty()),
            imageFile of MultipartFormFile(
                img.name,
                ContentType.OCTET_STREAM,
                img.file.inputStream(),
            )
        )

        Request(Method.POST, "http://localhost:8000").with(strictFormBody of body)
    }

    rqs.forEach { r -> println(ApacheClient()(r)) }


    metadata.forEach { m -> println("ratio: ${m.ratio}  ----- name: ${m.name}") }
}