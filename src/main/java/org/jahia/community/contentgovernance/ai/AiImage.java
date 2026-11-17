package org.jahia.community.contentgovernance.ai;

import java.util.Arrays;

/**
 * One picture sent with a prompt.
 *
 * @param mediaType its MIME type, such as {@code image/jpeg}
 * @param data      its encoded bytes
 */
public record AiImage(String mediaType, byte[] data) {

    public AiImage {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AiImage other && mediaType.equals(other.mediaType) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return 31 * mediaType.hashCode() + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "AiImage[mediaType=" + mediaType + ", bytes=" + data.length + "]";
    }
}
