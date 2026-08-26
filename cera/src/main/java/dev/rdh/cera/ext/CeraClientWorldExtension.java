package dev.rdh.cera.ext;

import dev.rdh.cera.entity.SpawnSnapshot;
import dev.rdh.cera.modules.DynamicLights;

import java.util.Map;
import java.util.UUID;

public interface CeraClientWorldExtension {
    default DynamicLights cera$getDynamicLights() {
        throw new UnsupportedOperationException();
    }

    default Map<UUID, SpawnSnapshot> cera$getSpawnSnapshots() {
        throw new UnsupportedOperationException();
    }
}
