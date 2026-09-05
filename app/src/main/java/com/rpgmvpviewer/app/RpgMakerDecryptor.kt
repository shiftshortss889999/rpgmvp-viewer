package com.rpgmvpviewer.app

import org.json.JSONObject

/**
 * Реализация алгоритма расшифровки картинок RPG Maker MV/MZ (.rpgmvp, .png_).
 *
 * Формат файла:
 *  - первые 16 байт — служебный заголовок RPG Maker (сигнатура "RPGMV..."), отбрасывается;
 *  - следующие 16 байт зашифрованы побайтовым XOR с ключом из System.json (encryptionKey);
 *  - остальные байты идут как есть — это обычные данные PNG.
 *
 * После расшифровки первых 16 байт получается стандартная PNG-сигнатура,
 * и итоговый массив байт можно скормить обычному BitmapFactory.
 */
object RpgMakerDecryptor {

    private const val HEADER_LEN = 16
    private const val ENCRYPTED_LEN = 16

    /**
     * Расшифровывает содержимое .rpgmvp/.png_ файла.
     * Возвращает null, если данные слишком короткие или ключ некорректен.
     */
    fun decryptImage(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size < HEADER_LEN + ENCRYPTED_LEN) return null
        if (key.size < ENCRYPTED_LEN) return null

        val body = data.copyOfRange(HEADER_LEN, data.size)
        val result = ByteArray(body.size)

        for (i in 0 until ENCRYPTED_LEN) {
            result[i] = (body[i].toInt() xor key[i].toInt()).toByte()
        }
        System.arraycopy(body, ENCRYPTED_LEN, result, ENCRYPTED_LEN, body.size - ENCRYPTED_LEN)
        return result
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length - 1) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    /** Достаёт encryptionKey из содержимого файла System.json. */
    fun extractKeyFromSystemJson(jsonText: String): ByteArray? {
        return try {
            val obj = JSONObject(jsonText)
            if (!obj.has("encryptionKey")) return null
            val hex = obj.getString("encryptionKey")
            if (hex.isBlank()) return null
            hexStringToByteArray(hex)
        } catch (e: Exception) {
            null
        }
    }
}
