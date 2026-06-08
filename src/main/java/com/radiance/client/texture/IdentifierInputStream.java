package com.radiance.client.texture;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.util.Identifier;

public class IdentifierInputStream extends InputStream {

    private final Identifier resourceId;
    private final InputStream originalStream;

    public IdentifierInputStream(InputStream originalStream, Identifier id) {
        this.resourceId = id;
        this.originalStream = originalStream;
    }

    public Identifier getResourceId() {
        return this.resourceId;
    }

    @Override
    public int read() throws IOException {
        return originalStream.read();
    }
}
