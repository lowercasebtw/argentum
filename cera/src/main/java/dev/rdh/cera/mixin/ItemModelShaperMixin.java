package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.item.ItemModelShaper;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelShaper.class)
public class ItemModelShaperMixin {
    @ModifyReturnValue(method = "getModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/resource/model/BakedModel;", at = @At("RETURN"))
    private BakedModel cera$resolveCustomItem(BakedModel original, ItemStack stack) {
        return Minecraft.getInstance().cera$getCustomItems().resolve(stack, original, null);
    }
}
