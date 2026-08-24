package dev.rdh.cera.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextureManager.class)
public class TextureManagerMixin {
    @ModifyVariable(method = "bind", at = @At("HEAD"), argsOnly = true)
    private Identifier cera$resolveCustomGui(Identifier texture) {
		return Minecraft.getInstance().cera$getCustomGuis().resolve(texture);
    }
}
