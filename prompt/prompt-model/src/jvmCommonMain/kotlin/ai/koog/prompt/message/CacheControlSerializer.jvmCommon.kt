package ai.koog.prompt.message

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializerOrNull

@OptIn(InternalSerializationApi::class)
internal actual fun loadCacheControlSerializer(qualifiedName: String): KSerializer<CacheControl>? {
    val clazz = loadClass(qualifiedName) ?: return null
    if (!CacheControl::class.java.isAssignableFrom(clazz)) return null
    val serializer = clazz.kotlin.serializerOrNull() ?: return null
    @Suppress("UNCHECKED_CAST")
    return serializer as KSerializer<CacheControl>
}

private fun loadClass(qualifiedName: String): Class<*>? {
    var name = qualifiedName
    while (true) {
        try {
            return Class.forName(name)
        } catch (_: ClassNotFoundException) {
            val separator = name.lastIndexOf('.')
            if (separator <= 0) return null
            name = name.substring(0, separator) + '$' + name.substring(separator + 1)
        }
    }
}
