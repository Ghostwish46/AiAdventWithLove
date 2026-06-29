package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock

interface InvariantsStore {
    fun loadBlocks(): List<InvariantBlock>
    fun saveBlocks(blocks: List<InvariantBlock>)
}
