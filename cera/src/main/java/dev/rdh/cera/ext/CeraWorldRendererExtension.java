package dev.rdh.cera.ext;

import dev.rdh.cera.modules.CustomSky;

public interface CeraWorldRendererExtension {
    default CustomSky cera$getCustomSky() {
        throw new UnsupportedOperationException();
    }
}
