import java.io.*
import java.net.*
import java.util.*

const val CRLF = "\r\n"
const val twoHyphens = "--"

fun main() {
    val props = Properties().apply { load(FileInputStream("config.properties")) }
    val botToken = props.getProperty("botToken")
    val hostname = props.getProperty("hostname")
    val port = props.getProperty("port").toInt()

    setWebhook(botToken, "https://$hostname:$port/$botToken", File("cert/public.pem"))
    val webhookInfo = getWebhookInfo(botToken)
    println(webhookInfo)
}

fun setWebhook(botToken: String, url: String, certificate: File) {
    val connection = URL("https://api.telegram.org/bot$botToken/setWebhook").openConnection() as HttpURLConnection
    connection.doOutput = true
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=*****")

    DataOutputStream(connection.outputStream).use { outputStream ->
        // Add data fields
        mapOf("url" to url).forEach { (key, value) ->
            outputStream.writeBoundary()
            outputStream.writeBytes("Content-Disposition: form-data; name=\"$key\"$CRLF")
            outputStream.writeBytes("Content-Type: text/plain; charset=UTF-8$CRLF")
            outputStream.writeBytes(CRLF)
            outputStream.writeBytes(value + CRLF)
        }

        // Add certificate file
        outputStream.writeBoundary()
        outputStream.writeBytes("Content-Disposition: form-data; name=\"certificate\"; filename=\"public.pem\"$CRLF")
        outputStream.writeBytes("Content-Type: application/octet-stream$CRLF")
        outputStream.writeBytes(CRLF)
        FileInputStream(certificate).use { input ->
            input.copyTo(outputStream)
        }
        outputStream.writeBytes(CRLF)

        // Add closing boundary
        outputStream.writeBoundary(true)
    }

    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        error("Error setting webhook: HTTP status code ${connection.responseCode}")
    }
}

fun getWebhookInfo(botToken: String): String {
    val connection = URL("https://api.telegram.org/bot$botToken/getWebhookInfo").openConnection() as HttpURLConnection
    connection.requestMethod = "GET"

    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        error("Error getting webhook info: HTTP status code ${connection.responseCode}")
    }

    return connection.inputStream.bufferedReader().use { it.readText() }
}

fun DataOutputStream.writeBoundary(last: Boolean = false) {
    val boundary = "*****"
    writeBytes(twoHyphens + boundary + (if (last) twoHyphens else "") + CRLF)
}

fun DataOutputStream.writeFormDataFile(key: String, file: File) {
    writeBoundary()
    writeBytes("Content-Disposition: form-data; name=\"$key\"; filename=\"${file.name}\"$CRLF")
    writeBytes("Content-Type: application/octet-stream$CRLF")
    writeBytes(CRLF)
    FileInputStream(file).use { input ->
        input.copyTo(this)
    }
    writeBytes(CRLF)
}
