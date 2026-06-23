package com.biliclient.core

/**
 * murmur3 x64 128-bit，用于生成 buvid_fp。
 * 权威来源：bili23 auth/cookie.py:56-150 get_buvid_fp（key=UA(ascii), seed=31）。
 * buvid_fp = hex(low64)[去0x] + hex(high64)[去0x]（low 在前，high 在后）。
 *
 * 注意：所有 64 位运算在 Kotlin Long 上天然 mod 2^64；fmix64 的右移用无符号 ushr。
 */
object Murmur3 {
    private val C1 = 0x87C37B911142253DUL.toLong()
    private val C2 = 0x4CF5AD432745937FUL.toLong()

    /** 返回 (low, high) */
    fun x64_128(data: ByteArray, seed: Int): Pair<Long, Long> {
        var h1 = seed.toLong()
        var h2 = seed.toLong()
        val n = data.size
        val nblocks = n / 16

        for (i in 0 until nblocks) {
            var k1 = leLong(data, i * 16)
            var k2 = leLong(data, i * 16 + 8)

            k1 *= C1; k1 = java.lang.Long.rotateLeft(k1, 31); k1 *= C2; h1 = h1 xor k1
            h1 = java.lang.Long.rotateLeft(h1, 27); h1 += h2; h1 = h1 * 5 + 0x52dce729
            k2 *= C2; k2 = java.lang.Long.rotateLeft(k2, 33); k2 *= C1; h2 = h2 xor k2
            h2 = java.lang.Long.rotateLeft(h2, 31); h2 += h1; h2 = h2 * 5 + 0x38495ab5
        }

        var k1 = 0L
        var k2 = 0L
        val tail = nblocks * 16
        val rem = n and 15
        if (rem >= 15) k2 = k2 xor ((data[tail + 14].toLong() and 0xff) shl 48)
        if (rem >= 14) k2 = k2 xor ((data[tail + 13].toLong() and 0xff) shl 40)
        if (rem >= 13) k2 = k2 xor ((data[tail + 12].toLong() and 0xff) shl 32)
        if (rem >= 12) k2 = k2 xor ((data[tail + 11].toLong() and 0xff) shl 24)
        if (rem >= 11) k2 = k2 xor ((data[tail + 10].toLong() and 0xff) shl 16)
        if (rem >= 10) k2 = k2 xor ((data[tail + 9].toLong() and 0xff) shl 8)
        if (rem >= 9) {
            k2 = k2 xor (data[tail + 8].toLong() and 0xff)
            k2 *= C2; k2 = java.lang.Long.rotateLeft(k2, 33); k2 *= C1; h2 = h2 xor k2
        }
        if (rem >= 8) k1 = k1 xor ((data[tail + 7].toLong() and 0xff) shl 56)
        if (rem >= 7) k1 = k1 xor ((data[tail + 6].toLong() and 0xff) shl 48)
        if (rem >= 6) k1 = k1 xor ((data[tail + 5].toLong() and 0xff) shl 40)
        if (rem >= 5) k1 = k1 xor ((data[tail + 4].toLong() and 0xff) shl 32)
        if (rem >= 4) k1 = k1 xor ((data[tail + 3].toLong() and 0xff) shl 24)
        if (rem >= 3) k1 = k1 xor ((data[tail + 2].toLong() and 0xff) shl 16)
        if (rem >= 2) k1 = k1 xor ((data[tail + 1].toLong() and 0xff) shl 8)
        if (rem >= 1) {
            k1 = k1 xor (data[tail].toLong() and 0xff)
            k1 *= C1; k1 = java.lang.Long.rotateLeft(k1, 31); k1 *= C2; h1 = h1 xor k1
        }

        h1 = h1 xor n.toLong(); h2 = h2 xor n.toLong()
        h1 += h2; h2 += h1
        h1 = fmix64(h1); h2 = fmix64(h2)
        h1 += h2; h2 += h1
        return h1 to h2
    }

    private fun fmix64(k0: Long): Long {
        var k = k0
        k = k xor (k ushr 33); k *= 0xFF51AFD7ED558CCDUL.toLong()
        k = k xor (k ushr 33); k *= 0xC4CEB9FE1A85EC53UL.toLong()
        k = k xor (k ushr 33)
        return k
    }

    private fun leLong(b: ByteArray, off: Int): Long {
        var r = 0L
        for (i in 0..7) r = r or ((b[off + i].toLong() and 0xff) shl (8 * i))
        return r
    }
}

/** 生成 buvid_fp（与 bili23 一致：hex(low) + hex(high)，无前导 0x，小写，不补零） */
fun genBuvidFp(ua: String): String {
    val (low, high) = Murmur3.x64_128(ua.toByteArray(Charsets.US_ASCII), 31)
    // Python hex(x & (2^64-1))[2:]：无前导零小写 hex。Long.toHexString 对负 Long 输出无符号 16 位 hex，与之一致。
    return java.lang.Long.toHexString(low) + java.lang.Long.toHexString(high)
}
