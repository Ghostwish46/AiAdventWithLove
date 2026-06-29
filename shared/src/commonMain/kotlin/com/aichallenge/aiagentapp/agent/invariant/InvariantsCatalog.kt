package com.aichallenge.aiagentapp.agent.invariant

class InvariantsCatalog(
    initialBlocks: List<InvariantBlock> = emptyList()
) {
    private var blocks: List<InvariantBlock> = initialBlocks

    fun allBlocks(): List<InvariantBlock> = blocks

    fun enabledBlocks(): List<InvariantBlock> = blocks.filter { it.enabled }

    fun getBlock(id: String): InvariantBlock? = blocks.find { it.id == id }

    fun blocksByIds(ids: List<String>): List<InvariantBlock> =
        ids.mapNotNull { id -> blocks.find { it.id == id && it.enabled } }

    fun replaceAll(newBlocks: List<InvariantBlock>) {
        blocks = newBlocks
    }

    fun addBlock(block: InvariantBlock) {
        blocks = blocks + block
    }

    fun updateBlock(block: InvariantBlock) {
        blocks = blocks.map { if (it.id == block.id) block else it }
    }

    fun removeBlock(id: String) {
        blocks = blocks.filter { it.id != id }
    }
}
