package com.example.proxy

import java.security.SecureRandom
import java.nio.ByteBuffer

object ClientHelloBuilder {
    private val random = SecureRandom()

    // Decoded equivalent of candidate HEX bytes from Go script
    private val cipherSuites = hexToBytes("0024130213031301c02cc030c02bc02fcca9cca8c024c028c023c027009f009e006b006700ff")
    private val supportedGroups = hexToBytes("000a00160014001d0017001e0019001801000101010201030104")
    private val signatureAlgorithms = hexToBytes("000d002a0028040305030603080708080809080a080b080408050806040105010601030303010302040205020602")
    private val ecPointFormats = hexToBytes("000b000403000102")
    private val sessionTicket = hexToBytes("00230000")
    private val alpn = hexToBytes("0010000e000c026832086874702f312e31")
    private val encryptThenMAC = hexToBytes("00160000")
    private val extendedMasterSecret = hexToBytes("00170000")
    private val supportedVersions = hexToBytes("002b00050403040303")
    private val pskKeyExchange = hexToBytes("002d00020101")

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun concat(vararg slices: ByteArray): ByteArray {
        var total = 0
        for (s in slices) {
            total += s.size
        }
        val result = ByteArray(total)
        var offset = 0
        for (s in slices) {
            System.arraycopy(s, 0, result, offset, s.size)
            offset += s.size
        }
        return result
    }

    fun buildSNIExtension(sni: String): ByteArray {
        val sniBytes = sni.toByteArray(Charsets.US_ASCII)
        val entry = ByteArray(3 + sniBytes.size)
        entry[0] = 0 // host_name type
        entry[1] = (sniBytes.size shr 8).toByte()
        entry[2] = (sniBytes.size and 0xFF).toByte()
        System.arraycopy(sniBytes, 0, entry, 3, sniBytes.size)

        val nameList = ByteArray(2 + entry.size)
        nameList[0] = (entry.size shr 8).toByte()
        nameList[1] = (entry.size and 0xFF).toByte()
        System.arraycopy(entry, 0, nameList, 2, entry.size)

        val result = ByteArray(4 + nameList.size)
        result[0] = 0x00
        result[1] = 0x00 // SNI extension type
        result[2] = (nameList.size shr 8).toByte()
        result[3] = (nameList.size and 0xFF).toByte()
        System.arraycopy(nameList, 0, result, 4, nameList.size)
        return result
    }

    fun buildKeyShareExtension(publicKey: ByteArray?): ByteArray {
        val finalPubKey = publicKey ?: ByteArray(32).also { random.nextBytes(it) }
        val entry = ByteArray(4 + finalPubKey.size)
        entry[0] = 0x00
        entry[1] = 0x1D // X25519 Group
        entry[2] = 0x00
        entry[3] = 0x20 // 32 bytes
        System.arraycopy(finalPubKey, 0, entry, 4, finalPubKey.size)

        val data = ByteArray(2 + entry.size)
        data[0] = (entry.size shr 8).toByte()
        data[1] = (entry.size and 0xFF).toByte()
        System.arraycopy(entry, 0, data, 2, entry.size)

        val result = ByteArray(4 + data.size)
        result[0] = 0x00
        result[1] = 0x33 // key_share extension type (51)
        result[2] = (data.size shr 8).toByte()
        result[3] = (data.size and 0xFF).toByte()
        System.arraycopy(data, 0, result, 4, data.size)
        return result
    }

    fun buildPaddingExtension(targetLength: Int, currentLength: Int): ByteArray? {
        val paddingNeeded = targetLength - currentLength - 4
        if (paddingNeeded < 0) return null
        val result = ByteArray(4 + paddingNeeded)
        result[0] = 0x00
        result[1] = 0x15 // Padding extension type (21)
        result[2] = (paddingNeeded shr 8).toByte()
        result[3] = (paddingNeeded and 0xFF).toByte()
        return result
    }

    fun buildClientHello(
        sni: String,
        sessionId: ByteArray? = null,
        randomBytes: ByteArray? = null,
        keyShare: ByteArray? = null,
        targetSize: Int = 517
    ): ByteArray {
        val finalSessionId = sessionId ?: ByteArray(32).also { random.nextBytes(it) }
        val finalRandom = randomBytes ?: ByteArray(32).also { random.nextBytes(it) }

        val clientVersion = byteArrayOf(0x03, 0x03)
        val sessionIdField = byteArrayOf(finalSessionId.size.toByte()) + finalSessionId
        val compression = byteArrayOf(0x01, 0x00)
        
        val sniExt = buildSNIExtension(sni)
        val keyShareExt = buildKeyShareExtension(keyShare)

        var extensions = concat(
            sniExt, ecPointFormats, supportedGroups,
            sessionTicket, alpn, encryptThenMAC,
            extendedMasterSecret, signatureAlgorithms,
            supportedVersions, pskKeyExchange, keyShareExt
        )

        val handshakeBodyNoPad = concat(
            clientVersion, finalRandom, sessionIdField,
            cipherSuites, compression
        )

        // handshaketype (1) + length (3) + body_no_pad_len + ext_len_field (2) + extensions_len
        val totalSoFar = 4 + handshakeBodyNoPad.size + 2 + extensions.size
        // record_header (5) + totalSoFar
        val recordSoFar = 5 + totalSoFar
        val paddingExt = buildPaddingExtension(targetSize, recordSoFar)
        if (paddingExt != null) {
            extensions = concat(extensions, paddingExt)
        }

        val extWithLen = ByteArray(2 + extensions.size)
        extWithLen[0] = (extensions.size shr 8).toByte()
        extWithLen[1] = (extensions.size and 0xFF).toByte()
        System.arraycopy(extensions, 0, extWithLen, 2, extensions.size)

        val handshakeBody = concat(handshakeBodyNoPad, extWithLen)
        val hsLen = handshakeBody.size
        
        val handshake = ByteArray(4 + hsLen)
        handshake[0] = 0x01 // ClientHello handshake type
        handshake[1] = ((hsLen shr 16) and 0xFF).toByte()
        handshake[2] = ((hsLen shr 8) and 0xFF).toByte()
        handshake[3] = (hsLen and 0xFF).toByte()
        System.arraycopy(handshakeBody, 0, handshake, 4, hsLen)

        val record = ByteArray(5 + handshake.size)
        record[0] = 0x16 // Handshake Record
        record[1] = 0x03 // TLS 1.0 Version Major
        record[2] = 0x01 // TLS 1.0 Version Minor
        record[3] = ((handshake.size shr 8) and 0xFF).toByte()
        record[4] = (handshake.size and 0xFF).toByte()
        System.arraycopy(handshake, 0, record, 5, handshake.size)

        return record
    }
}
