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
        3.195542.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "320_100",
        6.009615.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "300_50",
        1.0.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "250_250",
        0.6666667.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "320_480",
        6.408.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "320_50",
        0.50.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "300_600",
        0.26666668.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "160_600",
        3.879086.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "970_250",
        8.088889.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "728_90",
        1.19965.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble() to "300_250",
    )
// 667.0 x 2500.0 = 0.2668, ERR
// 4042.0 x 1042.0 = 3.8790786948176583, ERR
// 1400.0 x 1167.0 = 1.1996572407883461, ERR
// 1333.0 x 208.0 = 6.408653846153846, 320_50
// 1333.0 x 2000.0 = 0.6665, ERR
// 1333.0 x 417.0 = 3.196642685851319, ERR
// 1042.0 x 1042.0 = 1.0, 250_250
// 1250.0 x 208.0 = 6.009615384615385, 300_50
// 1250.0 x 2500.0 = 0.5, 300_600
// 3033.0 x 375.0 = 8.088, ERR
// 1250.0 x 1042.0 = 1.199616122840691, ERR
    val fp = args[0]

    val p = Paths.get(fp)
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
                            ratioToSize.getOrDefault(z.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toDouble(), "ERR")
                        println("$a x $b = $z, $s")
                        s
                    }, m.third, m.second?.imageSize
                    .let { i: ImageSize? ->
                        val arr = i.toString().split(" x ")
                        val a = arr[0].toDouble()
                        val b = arr[1].toDouble()
                        Pair(a, b)
                    }            )
        }.toMutableList()
     var n = metadata.filter{i->i.ratio == "300_250"}.sortedBy { it.size.first}.toMutableList()
    metadata.removeIf{i->i.ratio == "300_250"}
      n.set(1, image(n[1].name, "336_280", n[1].file, n[1].size))
     metadata.addAll(n)
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
