package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.JVMDocumentProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Path

/**
 * A JVM-specific implementation of [TextFileDocumentEmbeddingStorage] tailored for text document
 * embedding and storage within a file system. This class utilizes a [ai.koog.rag.base.files.JVMDocumentProvider] to handle
 * document reading and manages embeddings using a provided [ai.koog.embeddings.base.Embedder].
 *
 * @constructor Creates an instance of [JVMFileEmbeddingStorage].
 * @param embedder The embedding implementation used to generate and compare vector embeddings.
 * @param root The root directory where the document storage system is initialized.
 */
public class JVMFileEmbeddingStorage(
    embedder: Embedder,
    root: Path
) : TextFileDocumentEmbeddingStorage<Path, Path>(
    embedder = embedder,
    documentProvider = JVMDocumentProvider,
    fs = JVMFileSystemProvider.ReadWrite,
    root = root
)
