package dev.rdh.cera.modules.cit;

import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.util.math.Direction;

import java.util.List;

record TransformedModel(BakedModel model, ModelTransformations transformations) implements BakedModel {
    @Override
    public List<BakedQuad> getQuads(Direction face) {
        return model.getQuads(face);
    }

    @Override
    public List<BakedQuad> getQuads() {
        return model.getQuads();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return model.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return model.isGui3d();
    }

    @Override
    public boolean isCustomRenderer() {
        return model.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return model.getParticleIcon();
    }

    @Override
    public ModelTransformations getTransformations() {
        return transformations;
    }
}
