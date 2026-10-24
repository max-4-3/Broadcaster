package trust.me.broadcaster.dataclasses

data class SenderData(
    val id: String,
    val name: String,
    val content: String,
    val timestamp: Long,
    val extras: String // JSON encoded extras
)
