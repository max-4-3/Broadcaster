package trust.me.broadcaster.dataclasses

import androidx.annotation.Keep


@Keep
data class ExternalConfig(
    val version: String,
    val reactors: List<ReactorConfig>,
    val settings: Map<String, Any>,
)
