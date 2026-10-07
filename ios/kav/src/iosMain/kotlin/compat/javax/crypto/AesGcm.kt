// AES-GCM (NIST SP 800-38D) in pure Kotlin. The only caller is Pay.kt's Seal, which encrypts a few hundred bytes
// of session data at rest, so clarity beats speed. Verified against javax.crypto on the JVM for random inputs.
package javax.crypto

internal object AesGcm {
    private val SBOX = IntArray(256).also { s ->
        // generate the S-box instead of embedding a table: multiplicative inverse in GF(2^8) then the affine map
        var p = 1; var q = 1
        do {
            p = p xor ((p shl 1) and 0xFF) xor (if (p and 0x80 != 0) 0x1B else 0)       // p *= 3
            q = q xor (q shl 1); q = q xor (q shl 2); q = q xor (q shl 4); q = q and 0xFF  // q /= 3
            if (q and 0x80 != 0) q = q xor 0x09
            val x = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
            s[p] = (x xor 0x63) and 0xFF
        } while (p != 1)
        s[0] = 0x63
    }

    private fun rotl8(x: Int, n: Int) = ((x shl n) or (x ushr (8 - n))) and 0xFF

    private fun xtime(a: Int) = ((a shl 1) xor (if (a and 0x80 != 0) 0x1B else 0)) and 0xFF

    class Key(raw: ByteArray) {
        val rounds = when (raw.size) { 16 -> 10; 24 -> 12; 32 -> 14; else -> throw IllegalArgumentException("Invalid AES key length: ${raw.size} bytes") }
        val w = IntArray(4 * (rounds + 1))

        init {
            val nk = raw.size / 4
            for (i in 0 until nk) w[i] = ((raw[4 * i].toInt() and 0xFF) shl 24) or ((raw[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((raw[4 * i + 2].toInt() and 0xFF) shl 8) or (raw[4 * i + 3].toInt() and 0xFF)
            var rcon = 1
            for (i in nk until w.size) {
                var t = w[i - 1]
                if (i % nk == 0) { t = subWord((t shl 8) or (t ushr 24)) xor (rcon shl 24); rcon = xtime(rcon) }
                else if (nk > 6 && i % nk == 4) t = subWord(t)
                w[i] = w[i - nk] xor t
            }
        }

        private fun subWord(x: Int) = (SBOX[(x ushr 24) and 0xFF] shl 24) or (SBOX[(x ushr 16) and 0xFF] shl 16) or
            (SBOX[(x ushr 8) and 0xFF] shl 8) or SBOX[x and 0xFF]

        fun encryptBlock(inp: ByteArray, out: ByteArray) {
            val s = IntArray(16) { inp[it].toInt() and 0xFF }
            addRoundKey(s, 0)
            for (r in 1..rounds) {
                for (i in 0 until 16) s[i] = SBOX[s[i]]
                // shift rows (state is column-major: s[col*4+row])
                val t = s.copyOf()
                for (c in 0 until 4) for (row in 0 until 4) s[c * 4 + row] = t[((c + row) % 4) * 4 + row]
                if (r != rounds) for (c in 0 until 4) {
                    val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
                    s[c * 4] = xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3
                    s[c * 4 + 1] = a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3
                    s[c * 4 + 2] = a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)
                    s[c * 4 + 3] = (xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)
                }
                addRoundKey(s, r)
            }
            for (i in 0 until 16) out[i] = s[i].toByte()
        }

        private fun addRoundKey(s: IntArray, round: Int) {
            for (c in 0 until 4) {
                val k = w[round * 4 + c]
                s[c * 4] = s[c * 4] xor ((k ushr 24) and 0xFF); s[c * 4 + 1] = s[c * 4 + 1] xor ((k ushr 16) and 0xFF)
                s[c * 4 + 2] = s[c * 4 + 2] xor ((k ushr 8) and 0xFF); s[c * 4 + 3] = s[c * 4 + 3] xor (k and 0xFF)
            }
        }
    }

    /** Multiplication in GF(2^128) with the GCM bit order. */
    private fun gmul(x: ByteArray, h: ByteArray): ByteArray {
        val z = ByteArray(16); val v = h.copyOf()
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) for (j in 0 until 16) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            val lsb = v[15].toInt() and 1
            for (j in 15 downTo 1) v[j] = (((v[j].toInt() and 0xFF) ushr 1) or ((v[j - 1].toInt() and 1) shl 7)).toByte()
            v[0] = ((v[0].toInt() and 0xFF) ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xE1).toByte()
        }
        return z
    }

    private fun ghash(h: ByteArray, aad: ByteArray, ct: ByteArray): ByteArray {
        var y = ByteArray(16)
        fun feed(data: ByteArray) {
            var i = 0
            while (i < data.size) {
                val blk = ByteArray(16); data.copyInto(blk, 0, i, minOf(i + 16, data.size))
                for (j in 0 until 16) blk[j] = (blk[j].toInt() xor y[j].toInt()).toByte()
                y = gmul(blk, h); i += 16
            }
        }
        feed(aad); feed(ct)
        val lens = ByteArray(16)
        val a = aad.size.toLong() * 8; val c = ct.size.toLong() * 8
        for (i in 0 until 8) { lens[7 - i] = (a ushr (8 * i)).toByte(); lens[15 - i] = (c ushr (8 * i)).toByte() }
        for (j in 0 until 16) lens[j] = (lens[j].toInt() xor y[j].toInt()).toByte()
        return gmul(lens, h)
    }

    private fun counter0(key: Key, iv: ByteArray, h: ByteArray): ByteArray {
        if (iv.size == 12) return iv + byteArrayOf(0, 0, 0, 1)
        return ghash(h, ByteArray(0), iv)   // non-96-bit IVs: GHASH(IV) with length block
    }

    private fun ctr(key: Key, j0: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(data.size); val cb = j0.copyOf(); val ks = ByteArray(16)
        var i = 0
        while (i < data.size) {
            for (k in 15 downTo 12) { cb[k] = (cb[k] + 1).toByte(); if (cb[k] != 0.toByte()) break }
            key.encryptBlock(cb, ks)
            for (j in 0 until minOf(16, data.size - i)) out[i + j] = (data[i + j].toInt() xor ks[j].toInt()).toByte()
            i += 16
        }
        return out
    }

    private fun tag(key: Key, j0: ByteArray, h: ByteArray, aad: ByteArray, ct: ByteArray, tagLen: Int): ByteArray {
        val s = ghash(h, aad, ct); val ek = ByteArray(16); key.encryptBlock(j0, ek)
        return ByteArray(tagLen) { (s[it].toInt() xor ek[it].toInt()).toByte() }
    }

    /** Returns ciphertext followed by the tag, like javax.crypto's AES/GCM/NoPadding. */
    fun seal(rawKey: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0), tagBits: Int = 128): ByteArray {
        val key = Key(rawKey); val h = ByteArray(16).also { key.encryptBlock(ByteArray(16), it) }
        val j0 = counter0(key, iv, h)
        val ct = ctr(key, j0, plain)
        return ct + tag(key, j0, h, aad, ct, tagBits / 8)
    }

    /** Null when the tag does not match (wrong key, or the data was changed). */
    fun open(rawKey: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0), tagBits: Int = 128): ByteArray? {
        val tl = tagBits / 8
        if (sealed.size < tl) return null
        val key = Key(rawKey); val h = ByteArray(16).also { key.encryptBlock(ByteArray(16), it) }
        val j0 = counter0(key, iv, h)
        val ct = sealed.copyOfRange(0, sealed.size - tl)
        val expect = tag(key, j0, h, aad, ct, tl)
        var diff = 0
        for (i in 0 until tl) diff = diff or (expect[i].toInt() xor sealed[ct.size + i].toInt())
        if (diff != 0) return null
        return ctr(key, j0, ct)
    }
}
