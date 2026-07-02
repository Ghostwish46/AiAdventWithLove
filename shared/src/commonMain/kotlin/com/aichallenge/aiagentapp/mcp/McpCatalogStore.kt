package com.aichallenge.aiagentapp.mcp

interface McpCatalogStore {
    fun load(): McpCatalogPersisted
    fun save(catalog: McpCatalogPersisted)
}
