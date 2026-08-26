package dev.rdh.cera.entity;

import net.minecraft.entity.Entity;

import java.util.UUID;

public final class EntityContext {
    private Entity current;

    public Entity current() {
        return this.current;
    }

    public void begin(Entity entity) {
        this.current = entity;
    }

    public void end(Entity entity) {
        if (this.current == entity) this.current = null;
    }

    public static int seed(UUID uuid) {
        return (int) (uuid.getLeastSignificantBits() & 0x7FFFFFFFL);
    }
}
