package io.github.dovecoteescapee.byedpi.data

data class DomainList(
    val id: String,
    val name: String,
    val domains: List<String>,
    val isActive: Boolean = true,
    val isBuiltIn: Boolean = false
)