package dev.rdh.cera.ext;

import dev.rdh.cera.entity.EntityContext;

public interface CeraEntityRenderDispatcherExtension {
    default EntityContext cera$getEntityContext() {
        throw new UnsupportedOperationException();
    }
}
