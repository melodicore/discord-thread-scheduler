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
import me.datafox.dts.ExitCode.Companion.logAndExit
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import org.slf4j.LoggerFactory

class Main {
    private val log = LoggerFactory.getLogger(Main::class.java)

    fun main(args: Array<String>) {
        val parser = ArgParser(args, helpFormatter = DefaultHelpFormatter(epilogue = Strings.TOKEN_MESSAGE))
        val args =
            try {
                parser.parseInto(::DtsArgs).also { parser.force() }
            } catch (e: SystemExitException) {
                e.printAndExit("dts")
            }
        if (args.token != null && args.tokenFile != null) ExitCode.TOKEN_AND_TOKEN_FILE_SET.logAndExit(log)
        val token: String? = args.token ?: readToken(args.tokenFile) ?: System.getenv(Strings.TOKEN_ENV_VAR)
        if (token == null) ExitCode.NO_TOKEN_SET.logAndExit(log)
        val config = readConfig(args.configFile)
        val jda =
            try {
                JDABuilder.createDefault(token).build()
            } catch (_: InvalidTokenException) {
                ExitCode.INVALID_TOKEN.logAndExit(log)
            } catch (e: Throwable) {
                ExitCode.JDA_ERROR.logAndExit(log, e.message)
            }
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
                ExitCode.TOKEN_FILE_NOT_FOUND.logAndExit(log, path.toString())
            }
        if (token.isBlank()) ExitCode.TOKEN_FILE_EMPTY.logAndExit(log, path.toString())
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
            ExitCode.CONFIG_FILE_NOT_FOUND.logAndExit(log, path.toString())
        } catch (e: SerializationException) {
            ExitCode.CONFIG_FILE_INVALID_FORMAT.logAndExit(log, e.message)
        } catch (_: IllegalArgumentException) {
            ExitCode.CONFIG_FILE_INVALID_TYPE.logAndExit(log, path.toString())
        }
    }
}

internal class DtsArgs(parser: ArgParser) {
    val token by parser.storing("-t", "--token", help = "Discord bot token").default(null)

    val tokenFile by parser.storing("-f", "--token-file", help = "file containing Discord bot token").default(null)

    val configFile by parser.positional("CONFIG_FILE", "configuration file").default("config.json")
}
