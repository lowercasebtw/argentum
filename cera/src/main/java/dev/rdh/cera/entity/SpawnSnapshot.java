package dev.rdh.cera.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public record SpawnSnapshot(BlockPos pos, Biome biome) {
}
