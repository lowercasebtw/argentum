package dev.rdh.cera.mixin;

import dev.rdh.cera.modules.OptifineCosmetics;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<ClientPlayerEntity> {
    PlayerRendererMixin() {
        super(null, null, 0);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("RETURN"))
    private void cera$addOptifineCosmetics(EntityRenderDispatcher dispatcher, boolean thinArms, CallbackInfo ci) {
        this.addLayer(new OptifineCosmetics.Layer((PlayerRenderer)(Object)this));
    }
}
