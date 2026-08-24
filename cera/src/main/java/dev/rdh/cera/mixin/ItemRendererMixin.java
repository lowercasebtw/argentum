package dev.rdh.cera.mixin;

import dev.rdh.cera.modules.cit.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Shadow @Final
    private TextureManager textureManager;

    @Shadow
    private void render(BakedModel model, int color) {
    }

    @Shadow
    private void renderEnchantmentGlint(BakedModel model) {
    }

    @Redirect(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/model/ModelManager;getModel(Lnet/minecraft/client/resource/ModelIdentifier;)Lnet/minecraft/client/resource/model/BakedModel;"))
    private BakedModel cera$resolveCustomItemVariant(ModelManager manager, ModelIdentifier location, ItemStack stack,
                                                       LivingEntity entity, ModelTransformations.Type transform) {
        return Minecraft.getInstance().cera$getCustomItems().resolve(stack, manager.getModel(location), location);
    }

    @Redirect(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;renderEnchantmentGlint(Lnet/minecraft/client/resource/model/BakedModel;)V"))
    private void cera$renderCustomGlint(ItemRenderer renderer, BakedModel model, ItemStack stack) {
        var effects = Minecraft.getInstance().cera$getCustomItems().effects(stack);
        if (effects.isEmpty()) {
            this.renderEnchantmentGlint(model);
            return;
        }
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(5890);
        for (CustomItems.Effect effect : effects) {
            this.textureManager.bind(effect.texture());
            effect.blend().apply(1.0F);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(8.0F, 8.0F, 8.0F);
            GlStateManager.translatef(effect.speed() * (Minecraft.getTime() % 3000L) / 24000.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(effect.rotation(), 0.0F, 0.0F, 1.0F);
            this.render(model, -1);
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(5888);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
    }
}
