package dev.rdh.argentum.impl.ext;

public interface TextureGenerationExtension {
    default int argentum$getGeneration() {
        throw new UnsupportedOperationException();
    }
}
