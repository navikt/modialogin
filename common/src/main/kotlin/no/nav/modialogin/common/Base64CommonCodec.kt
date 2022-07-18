package no.nav.modialogin.common

object Base64CommonCodec {
    private const val PAD_DEFAULT: Byte = '='.code.toByte()
    private const val SPACE: Byte = ' '.code.toByte()
    private const val NEW_LINE: Byte = '\n'.code.toByte()
    private const val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
    private const val TAB: Byte = 't'.code.toByte()
    private const val NOT_FOUND: Byte = -1

    /**
     * Implementation grabbed from commons-codec
     *
     * https://github.com/apache/commons-codec
     * http://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     */

    private val DECODE_TABLE = byteArrayOf(
    //   0   1   2   3   4   5   6   7   8   9   A   B   C   D   E   F
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 00-0f
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 10-1f
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63,  // 20-2f + - /
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1,  // 30-3f 0-9
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,  // 40-4f A-O
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63,  // 50-5f P-Z _
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,  // 60-6f a-o
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51                       // 70-7a p-z
    )

    fun isBase64(base64: String): Boolean {
        return isBase64(base64.toByteArray(Charsets.UTF_8))
    }

    private fun isBase64(arrayOctet: ByteArray): Boolean {
        for (element in arrayOctet) {
            if (!isBase64(element) && !isWhiteSpace(element)) {
                return false
            }
        }
        return true
    }

    private fun isBase64(octet: Byte): Boolean {
        return octet == PAD_DEFAULT || octet >= 0 && octet < DECODE_TABLE.size && DECODE_TABLE[octet.toInt()] != NOT_FOUND
    }

    private fun isWhiteSpace(byteToCheck: Byte): Boolean {
        return when (byteToCheck) {
            SPACE, NEW_LINE, CARRIAGE_RETURN, TAB -> true
            else -> false
        }
    }
}