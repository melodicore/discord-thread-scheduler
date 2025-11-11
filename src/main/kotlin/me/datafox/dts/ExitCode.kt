package me.datafox.dts

import org.slf4j.Logger
import kotlin.system.exitProcess

enum class ExitCode(val code: Int, val message: (String?) -> String) {
    TOKEN_AND_TOKEN_FILE_SET(3, { "dts: please do not set both TOKEN and TOKEN_FILE arguments together" }),
    NO_TOKEN_SET(
        4,
        {
            "dts: Discord token must be specified with TOKEN or TOKEN_FILE argument, " +
                "or DISCORD_THREAD_SCHEDULER_TOKEN environment variable"
        },
    ),
    INVALID_TOKEN(5, { "dts: Discord token is invalid" }),
    JDA_ERROR(6, { "dts: ${it ?: "unknown JDA error"}" }),
    TOKEN_FILE_NOT_FOUND(7, { "dts: TOKEN_FILE was not found ($it)" }),
    TOKEN_FILE_EMPTY(8, { "dts: TOKEN_FILE was found but is empty ($it)" }),
    CONFIG_FILE_NOT_FOUND(9, { "dts: CONFIG_FILE was not found ($it)" }),
    CONFIG_FILE_INVALID_FORMAT(10, { "dts: CONFIG_FILE could not be deserialized ($it)" }),
    CONFIG_FILE_INVALID_TYPE(11, { "dts: CONFIG_FILE could not be deserialized ($it)" }),
    CHANNEL_NOT_FOUND(12, { "dts: channel not found ($it)" }),
    NOT_MESSAGE_CHANNEL(13, { "dts: channel is not a standard message channel ($it)" });

    companion object {
        fun ExitCode.logAndExit(logger: Logger, message: String? = null): Nothing {
            logger.error(message(message))
            exitProcess(code)
        }
    }
}
