package io.github.romanvht.byedpi.data

data class AppSettings(
    val app: String,
    val version: String,
    val history: List<Command>,
    val apps: List<String>,
    val domainLists: List<DomainList>? = null,
    val settings: Map<String, Any?>
)