import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.webhook
import com.google.gson.Gson
import data.AspectRatio
import data.Preferences
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.util.*

fun main() {
    val props = Properties().apply { load(FileInputStream("config.properties")) }

    val botToken = props.getProperty("botToken")
    val hostname = props.getProperty("hostname")
    val port = props.getProperty("port").toInt()
    val keyStoreFile = File("cert/keystore.jks")

    val bot = bot {
        token = botToken
        webhook {
            url = "https://$hostname:$port/$botToken"
            println("Webhook url: $url")
            certificate = TelegramFile.ByFile(keyStoreFile)
            maxConnections = 50
            allowedUpdates = listOf("message")
        }
        dispatch {
            text {
                handleMessage(bot, message.chat.id, message.text ?: "")
            }
        }
    }
    bot.startWebhook()
    bot.startPolling()

    val env = applicationEngineEnvironment {
        module {
            routing {
                post("/$botToken") {
                    val response = call.receiveText()
                    bot.processUpdate(response)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val keyStore: KeyStore by lazy {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(keyStoreFile.inputStream(), props.getProperty("keyStorePassword").toCharArray())
            ks
        }

        sslConnector(
            keyStore = keyStore,
            keyAlias = props.getProperty("keyAlias"),
            keyStorePassword = { props.getProperty("keyStorePassword").toCharArray() },
            privateKeyPassword = { props.getProperty("privateKeyPassword").toCharArray() }
        ) {
            this.port = port
            keyStorePath = keyStoreFile.absoluteFile
            host = "0.0.0.0"
        }
    }

    embeddedServer(Netty, env).start(wait = true)
}

enum class Command(val value: String) {
    START("/start"),
    IMAGE("/image"),
    HELP("/help"),
    WITHOUT("/without"),
    RATIO("/ratio");

    companion object {
        fun fromValue(text: String): Command? {
            return values().find { it.value == text }
        }
    }
}

fun handleMessage(bot: Bot, userId: Long, textMessage: String) {
    if (!File("users/$userId").exists()) {
        onStart(userId)
    }
    when (Command.fromValue(textMessage)) {
        Command.START -> {
            bot.sendMessage(
                ChatId.fromId(userId),
                "Hello! Use the command /image to generate an image"
            )
        }

        Command.IMAGE -> {
            bot.sendMessage(
                ChatId.fromId(userId),
                "Send me a text and I will generate an image"
            )
        }

        Command.HELP -> {
            bot.sendMessage(
                ChatId.fromId(userId),
                "Use the command /image to generate an image"
            )
        }

        Command.WITHOUT -> {
            bot.sendMessage(
                ChatId.fromId(userId),
                "What would you like not to be included in the generated image?"
            )
        }

        Command.RATIO -> {
            bot.sendMessage(
                ChatId.fromId(userId),
                "Choose your preferred image size ratio between ${AspectRatio.values().joinToString(", ") { it.textValue }}"
            )
        }

        else -> {
            handleCommandArgument(bot, userId, textMessage)
        }
    }

    Command.fromValue(textMessage)?.let { command ->
        setLastCommand(userId, command.value)
    }
    logUserCommand(userId, textMessage)
}

fun handleCommandArgument(bot: Bot, userId: Long, text: String) {
    when (Command.fromValue(getLastCommand(userId))) {
        Command.IMAGE -> {
            generateImage(userId = userId, prompt = text).let { success ->
                if (success) {
                    bot.sendPhoto(
                        ChatId.fromId(userId),
                        TelegramFile.ByFile(File("users/$userId/image.png"))
                    )
                } else {
                    bot.sendMessage(
                        ChatId.fromId(userId),
                        "Something went wrong"
                    )
                }
            }
        }

        Command.WITHOUT -> {
            val preferences = getPreferences(userId)
            preferences.without = text.trim()
            setPreferences(userId, preferences)
            bot.sendMessage(
                ChatId.fromId(userId),
                "I'll exclude that from the next images"
            )
        }

        Command.RATIO -> {
            if (AspectRatio.fromValue(text) == null) {
                bot.sendMessage(
                    ChatId.fromId(userId),
                    "Invalid ratio. Choose between ${AspectRatio.values().joinToString(", ") { it.textValue }}"
                )
                return
            }
            val preferences = getPreferences(userId)
            preferences.aspectRatio = text
            setPreferences(userId, preferences)
            bot.sendMessage(
                ChatId.fromId(userId),
                "Your preferred image size ratio is now $text"
            )
        }

        else -> {
            handleMessage(bot, userId, Command.HELP.value)
        }
    }
}

fun setPreferences(userId: Long, preferences: Preferences) {
    val file = File("users/$userId/preferences.json")
    file.writeText(Gson().toJson(preferences))
    setLastCommand(userId, Command.IMAGE.value)
}

fun getPreferences(userId: Long): Preferences {
    val file = File("users/$userId/preferences.json")
    return Gson().fromJson(BufferedReader(InputStreamReader(FileInputStream(file))), Preferences::class.java)
}

fun logUserCommand(userId: Long, text: String) {
    println("User $userId: $text")
    File("users/$userId/commands.txt").appendText("$text\n")
}

fun setLastCommand(userId: Long, text: String) = File("users/$userId/last_command.txt").writeText(text)

fun getLastCommand(user: Long): String = File("users/$user/last_command.txt").readText()

fun onStart(user: Long) {
    println("User $user started the bot")
    File("users/$user").let {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    File("users/$user/last_command.txt").let {
        if (!it.exists()) {
            it.createNewFile()
        }
    }
    File("users/$user/commands.txt").let {
        if (!it.exists()) {
            it.createNewFile()
        }
    }
    // preferences
    File("users/$user/preferences.json").let {
        if (!it.exists()) {
            it.createNewFile()
            it.writeText(Gson().toJson(Preferences()))
        }
    }
}

fun generateImage(userId: Long, prompt: String): Boolean {
    val preferences = getPreferences(userId)
    val ratio = AspectRatio.fromValue(preferences.aspectRatio) ?: AspectRatio.RATIO_1_1
    val processBuilder = ProcessBuilder(
        "python",
        "txt2img.py",
        "\"${prompt.replace("\"", "")}\"",
        "--filename","\"users/$userId/image\"",
        "--width","${ratio.width}",
        "--height","${ratio.height}",
        "--negative",preferences.without
    )
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start()

    val output = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    while (output.readLine().also { line = it } != null) {
        println(line)
    }

    val exitCode = process.waitFor()
    return exitCode == 0
}