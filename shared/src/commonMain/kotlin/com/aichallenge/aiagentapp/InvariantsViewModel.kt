package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.invariant.InvariantRule
import com.aichallenge.aiagentapp.agent.invariant.InvariantsCatalog
import com.aichallenge.aiagentapp.data.InvariantsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

class InvariantsViewModel(
    private val invariantsStore: InvariantsStore,
    private val onPersisted: (() -> Unit)? = null
) : ViewModel() {

    private val catalog = InvariantsCatalog(invariantsStore.loadBlocks())

    private val _blocks = MutableStateFlow(catalog.allBlocks())
    val blocks: StateFlow<List<InvariantBlock>> = _blocks.asStateFlow()

    fun reload() {
        catalog.replaceAll(invariantsStore.loadBlocks())
        _blocks.value = catalog.allBlocks()
    }

    fun saveBlock(
        id: String?,
        title: String,
        domainHint: String,
        rules: List<InvariantRule>,
        enabled: Boolean
    ): String {
        val blockId = id ?: newBlockId()
        val block = InvariantBlock(
            id = blockId,
            title = title.trim(),
            domainHint = domainHint.trim(),
            rules = rules.filter { it.text.isNotBlank() },
            enabled = enabled
        )
        if (id == null) {
            catalog.addBlock(block)
        } else {
            catalog.updateBlock(block)
        }
        persist()
        return blockId
    }

    fun deleteBlock(id: String) {
        catalog.removeBlock(id)
        persist()
    }

    fun getBlock(id: String): InvariantBlock? = catalog.getBlock(id)

    private fun persist() {
        invariantsStore.saveBlocks(catalog.allBlocks())
        _blocks.value = catalog.allBlocks()
        onPersisted?.invoke()
    }

    private fun newBlockId(): String =
        "inv_${Clock.System.now().toEpochMilliseconds()}_${
            (1..6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        }"

    fun newRuleId(): String =
        "rule_${Clock.System.now().toEpochMilliseconds()}_${
            (1..4).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        }"
}

fun createInvariantsCatalog(store: InvariantsStore): InvariantsCatalog =
    InvariantsCatalog(store.loadBlocks())
