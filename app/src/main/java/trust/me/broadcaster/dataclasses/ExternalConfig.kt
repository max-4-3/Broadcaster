package trust.me.broadcaster.dataclasses

import trust.me.broadcaster.AppSettings

data class ExternalConfig(
    val version: String,
    val reactors: List<ReactorConfig>,
    val settings: Map<AppSettings.Keys, String>,
)
