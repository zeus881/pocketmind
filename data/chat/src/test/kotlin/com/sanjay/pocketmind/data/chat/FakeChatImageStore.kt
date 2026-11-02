package com.sanjay.pocketmind.data.chat

/** [ChatImageStore] without files: an import of `content://x` "stores" `/images/x`. */
class FakeChatImageStore : ChatImageStore {
    val stored = mutableSetOf<String>()

    override suspend fun import(uri: String): String? =
        uri.removePrefix("content://").takeIf { it != uri }?.let { "/images/$it" }?.also { stored += it }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        stored.retainAll(keep)
    }

    override suspend fun delete(path: String) {
        stored -= path
    }
}
