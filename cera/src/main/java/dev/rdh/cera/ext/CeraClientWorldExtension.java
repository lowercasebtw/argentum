package dev.rdh.cera.ext;

import dev.rdh.cera.modules.DynamicLights;

public interface CeraClientWorldExtension {
    default DynamicLights cera$getDynamicLights() {
        throw new UnsupportedOperationException();
    }
}
