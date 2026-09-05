package com.rpgmvpviewer.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class ScanResult(
    val imageFiles: List<DocumentFile>,
    val encryptionKey: ByteArray?
)

/**
 * Рекурсивно обходит выбранную пользователем папку (через SAF/DocumentFile),
 * ищет файлы .rpgmvp/.png_ и файл System.json с ключом шифрования.
 */
object FileScanner {

    fun scan(context: Context, treeUri: Uri): ScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val images = mutableListOf<DocumentFile>()
        var systemJsonFile: DocumentFile? = null

        if (root != null) {
            walk(root) { doc ->
                val name = doc.name ?: return@walk
                when {
                    name.endsWith(".rpgmvp", ignoreCase = true) ||
                        name.endsWith(".png_", ignoreCase = true) -> images.add(doc)

                    name.equals("System.json", ignoreCase = true) -> systemJsonFile = doc
                }
            }
        }

        var key: ByteArray? = null
        systemJsonFile?.let { file ->
            try {
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val text = input.bufferedReader(Charsets.UTF_8).readText()
                    key = RpgMakerDecryptor.extractKeyFromSystemJson(text)
                }
            } catch (_: Exception) {
                // игнорируем — просто не нашли ключ
            }
        }

        images.sortBy { it.name?.lowercase() }
        return ScanResult(images, key)
    }

    private fun walk(dir: DocumentFile, onFile: (DocumentFile) -> Unit) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                walk(child, onFile)
            } else {
                onFile(child)
            }
        }
    }
}
