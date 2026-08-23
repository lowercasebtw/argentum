package dev.rdh.cera.ext;

import dev.rdh.cera.modules.DynamicLights;
import dev.rdh.cera.modules.OptifineCosmetics;

public interface CeraMinecraftExtension {
    default DynamicLights.Rules cera$getDynamicLightRules() {
        throw new UnsupportedOperationException();
    }

    default OptifineCosmetics cera$getOptifineCosmetics() {
        throw new UnsupportedOperationException();
    }
}
