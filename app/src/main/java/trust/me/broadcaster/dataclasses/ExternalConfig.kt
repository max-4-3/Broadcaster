package trust.me.broadcaster.dataclasses

data class ExternalConfig(
    val version: String,
    val reactors: List<ReactorConfig>,
    val settings: Map<String, Any>,
)
