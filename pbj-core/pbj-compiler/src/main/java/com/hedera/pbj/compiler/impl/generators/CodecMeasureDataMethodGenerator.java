package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;

import java.util.List;

/**
 * Code to generate the measure data method for Codec classes. This measures the size of bytes of data in the input to be parsed.
 */
class CodecMeasureDataMethodGenerator {

    static String generateMeasureMethod(final String modelClassName, final List<Field> fields) {
        // Placeholder implementation, replace faster implementation than full parse if there is one
        return """
                /**
                 * Reads from this data input the length of the data within the input. The implementation may
                 * read all the data, or just some special serialized data, as needed to find out the length of
                 * the data.
                 *
                 * @param input The input to use
                 * @return The length of the data item in the input
                 * @throws IOException If it is impossible to read from the {@link DataInput}
                 */
                public int measure(@NonNull DataInput input) throws IOException {
                    final long start = input.getPosition();
                    parse(input);
                    final long end = input.getPosition();
                    return (int)(end - start);
                }
                """
                .replaceAll("\n", "\n" + Common.FIELD_INDENT);
    }
}
