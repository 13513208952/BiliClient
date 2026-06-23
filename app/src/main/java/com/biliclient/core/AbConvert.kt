package com.biliclient.core

/**
 * av <-> bv 本地转换。
 * 权威来源（test1 优先）：ABConvert（旧版 BV 算法，10 位有效位）。
 * 已验证：av18190165 <-> BV1ZW411i7UY。
 */
object AbConvert {
    private val S = intArrayOf(11, 10, 3, 8, 4, 6, 2, 9, 5, 7)
    private const val XOR = 177451812L
    private const val ADD = 100618342136696320L
    private const val TABLE = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
    private val TR: Map<Char, Int> = TABLE.mapIndexed { i, c -> c to i }.toMap()

    private fun pow58(y: Int): Long {
        var r = 1L
        repeat(y) { r *= 58 }
        return r
    }

    /** "BV1ZW411i7UY" -> 18190165 */
    fun bvToAv(bv: String): Long {
        require(bv.length >= 12) { "invalid bv: $bv" }
        var num = 0L
        for (i in 0..9) {
            val c = bv[S[i]]
            val idx = TR[c] ?: error("invalid bv char '$c' in $bv")
            num += idx * pow58(i)
        }
        return (num - ADD) xor XOR
    }

    /** 18190165 -> "BV1ZW411i7UY" */
    fun avToBv(av: Long): String {
        var a = (av xor XOR) + ADD
        val arr = CharArray(10)
        for (i in 0..9) {
            arr[S[i] - 2] = TABLE[((a / pow58(i)) % 58).toInt()]
        }
        return "BV" + String(arr)
    }

    fun avToBv(av: String): String =
        avToBv(av.trim().removePrefix("av").removePrefix("AV").toLong())
}
