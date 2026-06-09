package com.example.proxy

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object ClientHelloParser {

    /**
     * Tries to parse real SNI from the TLS ClientHello packet.
     * Returns the SNI hostname, or null if not parsed.
     */
    fun parseSNI(data: ByteArray): String? {
        try {
            if (data.size < 5) return null
            
            val contentType = data[0].toInt() and 0xFF
            // 0x16 is Handshake record type
            if (contentType != 0x16) return null
            
            // Skip Record Header (Type: 1, Version: 2, Length: 2) -> 5 bytes
            var pos = 5
            if (pos + 4 > data.size) return null
            
            val handshakeType = data[pos].toInt() and 0xFF
            // 0x01 is ClientHello handshake type
            if (handshakeType != 1) return null
            
            // Skip Handshake header (Type: 1, Length: 3) -> 4 bytes
            pos += 4
            if (pos + 2 > data.size) return null
            
            // Version (2 bytes)
            pos += 2
            
            // Random (32 bytes)
            pos += 32
            if (pos >= data.size) return null
            
            // Session ID (1 + len bytes)
            val sessionIdLen = data[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 > data.size) return null
            
            // Cipher Suites (2 + len bytes)
            val cipherSuitesLen = readUint16(data, pos)
            pos += 2 + cipherSuitesLen
            if (pos >= data.size) return null
            
            // Compression Methods (1 + len bytes)
            val compressionLen = data[pos].toInt() and 0xFF
            pos += 1 + compressionLen
            if (pos + 2 > data.size) return null
            
            // Extensions (2 + len bytes)
            val extensionsLen = readUint16(data, pos)
            pos += 2
            val extEnd = pos + extensionsLen
            
            while (pos + 4 <= extEnd && pos + 4 <= data.size) {
                val extType = readUint16(data, pos)
                val extLen = readUint16(data, pos + 2)
                pos += 4
                
                if (pos + extLen > data.size) break
                
                // Server Name Indication (SNI) is type 0x0000
                if (extType == 0) {
                    val sniParsed = parseSniExtension(data, pos, extLen)
                    if (sniParsed != null) {
                        return sniParsed
                    }
                }
                pos += extLen
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseSniExtension(data: ByteArray, start: Int, length: Int): String? {
        try {
            var pos = start
            if (pos + 2 > start + length) return null
            
            // Server Name List Length (2 bytes)
            pos += 2
            
            while (pos + 3 <= start + length) {
                val nameType = data[pos].toInt() and 0xFF
                val nameLen = readUint16(data, pos + 1)
                pos += 3
                
                if (pos + nameLen > start + length) break
                
                // Name type 0 is host_name
                if (nameType == 0) {
                    val hostname = String(data, pos, nameLen, StandardCharsets.US_ASCII)
                    // Quick check if printable
                    if (hostname.all { it.code in 32..126 }) {
                        return hostname
                    }
                }
                pos += nameLen
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Find the offset where the SNI string literally starts inside the ClientHello array,
     * along with its length. Useful for `sni_split` strategy.
     */
    fun findSNIOffset(data: ByteArray): Pair<Int, Int>? {
        try {
            if (data.size < 10) return null
            
            // Standard scan for extension type 0x0000
            for (pos in 0 until data.size - 10) {
                // Look for SNI extension signature in some contexts or search sequentially:
                // Let's do a reliable scan based on finding the exact parsed SNI slice.
                val parsedSni = parseSNI(data) ?: continue
                val sniBytes = parsedSni.toByteArray(StandardCharsets.US_ASCII)
                
                // Locate these bytes precisely in the ClientHello
                val index = indexOf(data, sniBytes)
                if (index != -1) {
                    return Pair(index, sniBytes.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun indexOf(outer: ByteArray, inner: ByteArray): Int {
        for (i in 0..outer.size - inner.size) {
            var found = true
            for (j in inner.indices) {
                if (outer[i + j] != inner[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun readUint16(data: ByteArray, offset: Int): Int {
        val b1 = data[offset].toInt() and 0xFF
        val b2 = data[offset + 1].toInt() and 0xFF
        return (b1 shl 8) or b2
    }
}
