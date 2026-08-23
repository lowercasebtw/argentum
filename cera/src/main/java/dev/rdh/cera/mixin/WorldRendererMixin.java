package dev.rdh.cera.mixin;

import dev.rdh.argentum.impl.ext.WorldRendererExtension;
import dev.rdh.cera.Cera;
import dev.rdh.cera.ext.CeraWorldRendererExtension;
import dev.rdh.cera.modules.CustomSky;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin implements CeraWorldRendererExtension {
    @Shadow
    private ClientWorld world;

    @Unique
    private final CustomSky cera$customSky = new CustomSky();

    @Override
    public CustomSky cera$getCustomSky() {
        return this.cera$customSky;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cera$updateDynamicLights(CallbackInfo ci) {
        if (this.world != null) {
            var renderer = ((WorldRendererExtension)this).argentum$getWorldRenderer();
            this.world.cera$getDynamicLights().update(this.world, renderer);
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void cera$prepareCelestial(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        this.cera$customSky.prepareCelestial(this.world, tickDelta);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableFog()V"))
    private void cera$renderCustomSky(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        if (Cera.CONFIG.customSky) {
            this.cera$customSky.render(this.world, tickDelta);
            GlStateManager.depthMask(false);
            GlStateManager.disableTexture();
        }
    }

    @ModifyArg(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V", ordinal = 0))
    private Identifier cera$renderCustomSun(Identifier source) {
        return this.cera$customSky.resolveSun(source);
    }

    @ModifyArg(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V", ordinal = 1))
    private Identifier cera$renderCustomMoon(Identifier source) {
        return this.cera$customSky.resolveMoon(source);
    }
}
