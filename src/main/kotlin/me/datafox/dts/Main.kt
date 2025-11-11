package me.datafox.dts

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.JDABuilder
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class Main {
    private val log = LoggerFactory.getLogger(Main::class.java)

    fun main(args: Array<String>) {
        val parser =
            ArgParser(
                args,
                helpFormatter =
                    DefaultHelpFormatter(
                        epilogue =
                            "Discord bot token can also be set with the DISCORD_THREAD_SCHEDULER_TOKEN environment variable"
                    ),
            )
        val args =
            try {
                parser.parseInto(::DtsArgs).also { parser.force() }
            } catch (e: SystemExitException) {
                e.printAndExit("dts")
            }
        if (args.token != null && args.tokenFile != null) {
            log.error("dts: please do not set both TOKEN and TOKEN_FILE arguments together")
            exitProcess(3)
        }
        val token: String? = args.token ?: readToken(args.tokenFile) ?: System.getenv("DISCORD_THREAD_SCHEDULER_TOKEN")
        if (token == null) {
            log.error(
                "dts: Discord token must be specified with TOKEN or TOKEN_FILE argument, or DISCORD_THREAD_SCHEDULER_TOKEN environment variable"
            )
            exitProcess(4)
        }
        val config = readConfig(args.configFile)
        val jda = JDABuilder.createDefault(token).build()
        Scheduler.launch(config, jda)
    }

    fun readToken(tokenFile: String?): String? {
        if (tokenFile.isNullOrBlank()) return null
        val path =
            if (tokenFile[0] == '/' || tokenFile.length > 1 && tokenFile[1] == ':') Path(tokenFile)
            else Path(System.getProperty("user.dir"), tokenFile)
        val token =
            try {
                SystemFileSystem.source(path).buffered().use { it.readString().trim() }
            } catch (_: FileNotFoundException) {
                log.error("dts: TOKEN_FILE was not found ($path)")
                exitProcess(5)
            }
        if (token.isBlank()) {
            log.error("dts: TOKEN_FILE was found but is empty ($path)")
            exitProcess(6)
        }
        return token
    }

    fun readConfig(configFile: String): Config {
        val path =
            if (configFile[0] == '/' || configFile.length > 1 && configFile[1] == ':') Path(configFile)
            else Path(System.getProperty("user.dir"), configFile)
        val json = Json.Default
        return try {
            SystemFileSystem.source(path).buffered().use { json.decodeFromString(it.readString()) }
        } catch (_: FileNotFoundException) {
            log.error("dts: CONFIG_FILE was not found ($path)")
            exitProcess(7)
        } catch (e: SerializationException) {
            log.error("dts: CONFIG_FILE was found but could not be deserialized ($path)")
            e.message?.let { log.error(it) }
            exitProcess(8)
        } catch (_: IllegalArgumentException) {
            log.error("dts: CONFIG_FILE was found but could not be deserialized ($path)")
            exitProcess(9)
        }
    }
}

internal class DtsArgs(parser: ArgParser) {
    val token by parser.storing("-t", "--token", help = "Discord bot token").default(null)

    val tokenFile by parser.storing("-f", "--token-file", help = "file containing Discord bot token").default(null)

    val configFile by parser.positional("CONFIG_FILE", "configuration file").default("config.json")
}
