package dev.rdh.cera.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.block.BlockModel;
import net.minecraft.client.resource.model.ModelBakery;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Shadow @Final
    private ResourceManager resourceManager;
    @Shadow @Final
    private Map<Identifier, BlockModel> blockModels;
    @Shadow
    private Map<String, Identifier> itemModels;

    @Inject(method = "loadBuiltIn", at = @At("RETURN"))
    private void cera$loadCustomItems(CallbackInfo ci) {
        Minecraft.getInstance().cera$getCustomItems().registerModels(this.resourceManager, this.itemModels, this.blockModels);
    }

    @Inject(method = "getBakedModels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/model/ModelBakery;bakeModels()V"))
    private void cera$discardEmptyCustomItems(CallbackInfoReturnable<?> cir) {
        Minecraft.getInstance().cera$getCustomItems().discardEmptyModels(this.itemModels, this.blockModels);
    }
}
