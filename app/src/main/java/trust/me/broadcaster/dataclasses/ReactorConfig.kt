package trust.me.broadcaster.dataclasses

import androidx.annotation.Keep


@Keep
data class ReactorConfig(
    val name: String = "Untitled",
    val method: String = "POST", // "GET" or "POST"
    val endpoint: String = "",
    val bodyLua: String = """
        -- Example lua script
        
        function getOutput()
            return utils.toJson({
                text = string.format("%s@%d: %s", data.name, data.timestamp, data.content)
            })
        end
    """.trimIndent(),
    val ctype: String = "application/json",
    val expected: List<Int> = listOf(200, 204)
)
