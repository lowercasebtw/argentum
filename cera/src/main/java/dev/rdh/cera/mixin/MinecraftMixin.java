package dev.rdh.cera.mixin;

import dev.rdh.cera.ext.CeraMinecraftExtension;
import dev.rdh.cera.ext.CeraWorldRendererExtension;
import dev.rdh.cera.modules.DynamicLights;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin implements CeraMinecraftExtension {
    @Unique
    private final DynamicLights.Rules cera$dynamicLightRules = new DynamicLights.Rules();

    @Override
    public DynamicLights.Rules cera$getDynamicLightRules() {
        return this.cera$dynamicLightRules;
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void cera$clearCustomSkyTextures(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (minecraft.worldRenderer != null) {
            ((CeraWorldRendererExtension)minecraft.worldRenderer).cera$getCustomSky().texturesReloading(minecraft.getTextureManager());
        }
    }
}
