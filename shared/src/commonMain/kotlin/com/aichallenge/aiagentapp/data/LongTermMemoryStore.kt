package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.memory.LongTermMemory

interface LongTermMemoryStore {
    fun load(): LongTermMemory
    fun save(memory: LongTermMemory)
}
