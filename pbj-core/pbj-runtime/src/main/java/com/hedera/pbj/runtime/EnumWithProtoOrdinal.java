package com.hedera.pbj.runtime;

/**
 * Interface for enums that have a protobuf ordinal
 */
public interface EnumWithProtoOrdinal {
    /**
     * Get the Protobuf ordinal for this object
     *
     * @return integer ordinal
     */
    int protoOrdinal();
}
