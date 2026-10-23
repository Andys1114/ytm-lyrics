package dev.ytmlyrics.source;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

/**
 * Copyright 2026 absswds; Apache-2.0. Java port of QrcDecrypter.kt, revision
 * 827a0f483e7808b4b749adbffdd9227ae3035b89, itself ported from WXRIW/Lyricify-Lyrics-Helper.
 * QQ's S-boxes/bit ordering differ from standard DES; javax.crypto is not equivalent.
 * Modified: Java, validated blocks and bounded decompression, explicit IOException.
 */
public final class QrcCrypto {
    private static final byte[] KEY = "!@#)(*$%123ZXC!@!@#)(NHL".getBytes(StandardCharsets.US_ASCII);
    private QrcCrypto() {}
    public static String decrypt(String hex) throws IOException {
        if (hex == null || hex.isEmpty() || hex.length() % 16 != 0 || !hex.matches("[0-9A-Fa-f]+"))
            throw new IOException("QRC 加密数据无效");
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        byte[][][] schedule = schedule(false);
        byte[] block = new byte[8];
        for (int i = 0; i < data.length; i += 8) {
            crypt(Arrays.copyOfRange(data, i, i + 8), block, schedule[0]);
            crypt(block, block, schedule[1]); crypt(block, block, schedule[2]);
            System.arraycopy(block, 0, data, i, 8);
        }
        try (var input = new InflaterInputStream(new ByteArrayInputStream(data)); var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > 1024 * 1024) throw new IOException("QRC 解压内容过大");
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (IOException error) { throw new IOException("QRC 解密或解压失败", error); }
    }
    private static int bitNum(byte[] a, int b, int c) { return (((a[b / 32 * 4 + 3 - b % 32 / 8] & 255) >>> (7 - b % 8)) & 1) << c; }
    private static int bitNumIntR(int a, int b, int c) { return ((a >>> (31 - b)) & 1) << c; }
    private static int bitNumIntL(int a, int b, int c) { return ((a << b) & 0x80000000) >>> c; }
    private static int sboxBit(int a) { return (a & 0x20) | ((a & 0x1f) >> 1) | ((a & 1) << 4); }
    private static final int[] SBOX1 = {14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
        0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
        4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
        15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13};
    private static final int[] SBOX2 = {15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
        3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
        0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
        13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9};
    private static final int[] SBOX3 = {10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
        13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
        13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
        1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12};
    private static final int[] SBOX4 = {7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
        13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
        10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
        3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14};
    private static final int[] SBOX5 = {2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
        14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
        4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
        11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3};
    private static final int[] SBOX6 = {12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
        10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
        9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
        4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13};
    private static final int[] SBOX7 = {4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
        13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
        1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
        6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12};
    private static final int[] SBOX8 = {13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
        1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
        7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
        2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11};
    private static final int[] KEY_RND_SHIFT = {1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1};
    private static final int[] KEY_PERM_C = {56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
        9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35};
    private static final int[] KEY_PERM_D = {62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
        13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3};
    private static final int[] KEY_COMPRESSION = {13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
        22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31};
    private static final int[] P_PERM = {15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24};

    private static void keySchedule(byte[] key, byte[][] rows, boolean encrypt) {
        int c = 0, d = 0;
        for (int i = 0; i < 28; i++) { c |= bitNum(key, KEY_PERM_C[i], 31 - i); d |= bitNum(key, KEY_PERM_D[i], 31 - i); }
        for (int i = 0; i < 16; i++) {
            int s = KEY_RND_SHIFT[i];
            c = ((c << s) | (c >>> (28 - s))) & 0xfffffff0;
            d = ((d << s) | (d >>> (28 - s))) & 0xfffffff0;
            byte[] row = rows[encrypt ? i : 15 - i];
            for (int j = 0; j < 24; j++) row[j / 8] |= (byte) bitNumIntR(c, KEY_COMPRESSION[j], 7 - j % 8);
            for (int j = 24; j < 48; j++) row[j / 8] |= (byte) bitNumIntR(d, KEY_COMPRESSION[j] - 27, 7 - j % 8);
        }
    }
    private static byte[][][] schedule(boolean encrypt) {
        byte[][][] rows = new byte[3][16][6];
        keySchedule(KEY, rows[encrypt ? 0 : 2], encrypt);
        keySchedule(Arrays.copyOfRange(KEY, 8, KEY.length), rows[1], !encrypt);
        keySchedule(Arrays.copyOfRange(KEY, 16, KEY.length), rows[encrypt ? 2 : 0], encrypt);
        return rows;
    }
    private static void ip(int[] state, byte[] input) {
        int[] order = {57,49,41,33,25,17,9,1,59,51,43,35,27,19,11,3,61,53,45,37,29,21,13,5,63,55,47,39,31,23,15,7};
        for (int k = 0; k < 32; k++) { state[0] |= bitNum(input, order[k], 31-k); state[1] |= bitNum(input, order[k]-1,31-k); }
    }
    private static void invIp(int[] state, byte[] out) {
        int[] byteIndex = {4,5,6,7,0,1,2,3};
        for (int b = 0; b < 8; b++) out[byteIndex[b]] = (byte) (
            bitNumIntR(state[1],b,7) | bitNumIntR(state[0],b,6) |
            bitNumIntR(state[1],b+8,5) | bitNumIntR(state[0],b+8,4) |
            bitNumIntR(state[1],b+16,3) | bitNumIntR(state[0],b+16,2) |
            bitNumIntR(state[1],b+24,1) | bitNumIntR(state[0],b+24,0));
    }
    private static int f(int st, byte[] key) {
        int t1 = bitNumIntL(st,31,0) | ((st & 0xf0000000) >>> 1) | bitNumIntL(st,4,5) |
            bitNumIntL(st,3,6) | ((st & 0x0f000000) >>> 3) | bitNumIntL(st,8,11) |
            bitNumIntL(st,7,12) | ((st & 0x00f00000) >>> 5) | bitNumIntL(st,12,17) |
            bitNumIntL(st,11,18) | ((st & 0x000f0000) >>> 7) | bitNumIntL(st,16,23);
        int t2 = bitNumIntL(st,15,0) | ((st & 0x0000f000) << 15) | bitNumIntL(st,20,5) |
            bitNumIntL(st,19,6) | ((st & 0x00000f00) << 13) | bitNumIntL(st,24,11) |
            bitNumIntL(st,23,12) | ((st & 0x000000f0) << 11) | bitNumIntL(st,28,17) |
            bitNumIntL(st,27,18) | ((st & 0x0000000f) << 9) | bitNumIntL(st,0,23);
        int[] l = {(t1 >>> 24)&255,(t1 >>> 16)&255,(t1 >>> 8)&255,(t2 >>> 24)&255,(t2 >>> 16)&255,(t2 >>> 8)&255};
        for (int k = 0; k < 6; k++) l[k] ^= key[k] & 255;
        int s = (SBOX1[sboxBit(l[0] >> 2)] << 28) |
            (SBOX2[sboxBit(((l[0]&3)<<4) | (l[1]>>4))] << 24) |
            (SBOX3[sboxBit(((l[1]&15)<<2) | (l[2]>>6))] << 20) |
            (SBOX4[sboxBit(l[2]&63)] << 16) |
            (SBOX5[sboxBit(l[3]>>2)] << 12) |
            (SBOX6[sboxBit(((l[3]&3)<<4) | (l[4]>>4))] << 8) |
            (SBOX7[sboxBit(((l[4]&15)<<2) | (l[5]>>6))] << 4) | SBOX8[sboxBit(l[5]&63)];
        int result = 0;
        for (int k = 0; k < 32; k++) result |= bitNumIntL(s,P_PERM[k],k);
        return result;
    }
    private static void crypt(byte[] in, byte[] out, byte[][] key) {
        int[] state = new int[2]; ip(state,in);
        for (int i = 0; i < 15; i++) { int t = state[1]; state[1] = f(state[1],key[i]) ^ state[0]; state[0] = t; }
        state[0] = f(state[1],key[15]) ^ state[0]; invIp(state,out);
    }
}
