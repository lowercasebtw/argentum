package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.cera.modules.OptifineCosmetics;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @ModifyReturnValue(method = "getCapeTextureLocation", at = @At("RETURN"))
    private Identifier cera$loadOptifineCape(Identifier vanilla) {
        return OptifineCosmetics.cape((ClientPlayerEntity)(Object)this, vanilla);
    }
}
