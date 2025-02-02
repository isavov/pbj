package com.hedera.pbj.runtime.io;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * <p>A alternative to byte array that is immutable</p>
 *
 * <p>It is simple to implement in basic form with just the {@code getLength()} and {@code getByte(int offset)} method
 * needing implementing as all other get methods have public implementations. Though it will work, it should not be
 * used like that in performance critical cases as specialized get methods can be many times more efficient.</p>
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public abstract class Bytes {

    /** Single instance of an empty Bytes we can use anywhere we need an empty Bytes */
    public static final Bytes EMPTY_BYTES = new Bytes() {
        @Override
        public int getLength() {
            return 0;
        }

        @Override
        public byte getByte(int offset) {
            throw new BufferUnderflowException();
        }
    };

    // ================================================================================================================
    // Static Methods

    /**
     * Create a new Bytes over the contents of the given byte array. This does not copy data it just wraps so any
     * changes to arrays contents will be effected here.
     *
     * @param byteArray The byte array to wrap
     * @return new Bytes with same contents as byte array
     */
    public static Bytes wrap(byte[] byteArray) {
        // For now use ByteOverByteBuffer, could have better array based implementation later
        return new ByteOverByteBuffer(byteArray);
    }

    /**
     * Create a new Bytes with the contents of a String UTF8 encoded.
     *
     * @param string The string to UFT8 encode and create a bytes for
     * @return new Bytes with string contents UTF8 encoded
     */
    public static Bytes wrap(String string) {
        return wrap(string.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================================================================
    // Object Methods

    /**
     * toString that outputs data in buffer in bytes.
     *
     * @return nice debug output of buffer contents
     */
    @Override
    public String toString() {
        // build string
        StringBuilder sb = new StringBuilder();
        sb.append("Bytes[");
        for (int i = 0; i < getLength(); i++) {
            int v = getByte(i) & 0xFF;
            sb.append(v);
            if (i < (getLength()-1)) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Equals, important that it works for all subclasses of Bytes as well. As any 2 Bytes classes with same contents of
     * bytes are equal
     *
     * @param o the other Bytes object to compare to for equality
     * @return true if o instance of Bytes and contents match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bytes that)) return false;
        final int length = getLength();
        if (length != that.getLength()) {
            return false;
        }
        if (length == 0) return true;
        for (int i = 0; i < length; i++) {
            if (getByte(i) != that.getByte(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute hash code for Bytes based on all bytes of content
     *
     * @return unique for any given content
     */
    @Override
    public int hashCode() {
        int h = 1;
        for (int i = getLength() - 1; i >= 0; i--) {
            h = 31 * h + getByte(i);
        }
        return h;
    }

    // ================================================================================================================
    // Bytes Methods

    /**
     * Get the contents of this byte as a string, assuming bytes contained are UTF8 encoded string.
     *
     * @return Bytes data converted to string
     */
    public String asUtf8String() {
        byte[] data = new byte[getLength()];
        getBytes(0,data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Get the number of bytes of data stored
     *
     * @return number of bytes of data stored
     */
    public abstract int getLength();

    /**
     * Gets the byte at given {@code offset}.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset}
     * @throws BufferUnderflowException If the given {@code offset} is not smaller than its limit
     */
    public abstract byte getByte(int offset);

    /**
     * Gets the byte at given {@code offset} as unsigned.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset}
     * @throws BufferUnderflowException If the given {@code offset} is not smaller than its limit
     */
    public int getUnsignedByte(int offset) {
        return Byte.toUnsignedInt(getByte(offset));
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                no larger than {@code dst.length}
     * @param length The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                no larger than {@code dst.length - offset}
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining to be get
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     * not hold
     */
    public void getBytes(int offset, byte[] dst, int dstOffset, int length) {
        if ((offset + length) > getLength()) {
            throw new BufferUnderflowException();
        }
        if (dstOffset < 0 || (dstOffset + length) >= dst.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = getByte(offset + i);
        }
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code }dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination array
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining in this buffer
     */
    public void getBytes(int offset, byte[] dst) {
        getBytes(offset, dst, 0, dst.length);
    }

    /**
     * Get bytes starting at given {@code offset} into dst ByteBuffer up to remaining bytes in ByteBuffer.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination ByteBuffer
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this buffer
     */
    public void getBytes(int offset, ByteBuffer dst) {
        if ((offset + dst.remaining()) > getLength()) {
            throw new BufferUnderflowException();
        }
        while(dst.hasRemaining()) {
            dst.put(getByte(offset++));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an int value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public int getInt(int offset) {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset);
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an int value according to specified byte
     * order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public int getInt(int offset, ByteOrder byteOrder) {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an unsigned int value according to the
     * Java standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public long getUnsignedInt(int offset) {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset);
        return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an unsigned int value according to
     * specified byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public long getUnsignedInt(int offset, ByteOrder byteOrder) {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
            return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
        }
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a long value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @param offset The offset into data to get long at
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    public long getLong(int offset) {
        if ((getLength() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset++);
        final byte b5 = getByte(offset++);
        final byte b6 = getByte(offset++);
        final byte b7 = getByte(offset++);
        final byte b8 = getByte(offset);
        return (((long)b1 << 56) +
                ((long)(b2 & 255) << 48) +
                ((long)(b3 & 255) << 40) +
                ((long)(b4 & 255) << 32) +
                ((long)(b5 & 255) << 24) +
                ((b6 & 255) << 16) +
                ((b7 & 255) <<  8) +
                ((b8 & 255)));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a long value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param offset The offset into data to get long at
     * @param byteOrder the byte order, aka endian to use
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    public long getLong(int offset, ByteOrder byteOrder) {
        if ((getLength() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getLong(offset);
        } else {
            final byte b8 = getByte(offset++);
            final byte b7 = getByte(offset++);
            final byte b6 = getByte(offset++);
            final byte b5 = getByte(offset++);
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
            return (((long) b1 << 56) +
                    ((long) (b2 & 255) << 48) +
                    ((long) (b3 & 255) << 40) +
                    ((long) (b4 & 255) << 32) +
                    ((long) (b5 & 255) << 24) +
                    ((b6 & 255) << 16) +
                    ((b7 & 255) << 8) +
                    ((b8 & 255)));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into a float value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get float at
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public float getFloat(int offset) {
        return Float.intBitsToFloat(getInt(offset));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into a float value according to specified byte
     * order, and then increments the position by four.
     *
     * @param offset The offset into data to get float at
     * @param byteOrder the byte order, aka endian to use
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    public float getFloat(int offset, ByteOrder byteOrder) {
        return Float.intBitsToFloat(getInt(offset, byteOrder));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a double value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @param offset The offset into data to get double at
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    public double getDouble(int offset) {
        return Double.longBitsToDouble(getLong(offset));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a double value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param offset The offset into data to get dpuble at
     * @param byteOrder the byte order, aka endian to use
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    public double getDouble(int offset, ByteOrder byteOrder) {
        return Double.longBitsToDouble(getLong(offset, byteOrder));
    }

    /**
     * Get a 32bit protobuf varint at given {@code offset}. An integer var int can be 1 to 5 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return integer get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     */
    public int getVarInt(int offset, boolean zigZag) {
        return (int)getVarLong(offset, zigZag);
    }

    /**
     * Get a 64bit protobuf varint at given {@code offset}. A long var int can be 1 to 10 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return long get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     */
    public long getVarLong(int offset, boolean zigZag) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = getByte(offset++);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new RuntimeException("Malformed Varint");
    }

    /**
     * Check if the beginning of our bytes data matches the given prefix bytes.
     *
     * @param prefix the prefix bytes to compare with
     * @return true if prefix bytes match the beginning of our bytes
     */
    public boolean matchesPrefix(byte[] prefix) {
        if (prefix == null || getLength() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != getByte(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the beginning of our bytes data matches the given prefix bytes.
     *
     * @param prefix the prefix bytes to compare with
     * @return true if prefix bytes match the beginning of our bytes
     */
    public boolean matchesPrefix(Bytes prefix) {
        if (prefix == null || getLength() < prefix.getLength()) {
            return false;
        }
        for (int i = 0; i < prefix.getLength(); i++) {
            if (prefix.getByte(i) != getByte(i)) {
                return false;
            }
        }
        return true;
    }
}
