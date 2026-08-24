package dev.rdh.cera.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "useBlock", at = @At("HEAD"))
    private void cera$rememberBlock(LocalClientPlayerEntity player, ClientWorld world, ItemStack itemInHand, BlockPos pos,
                                    Direction face, Vec3d facePos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft.getInstance().cera$getCustomGuis().interactedWith(pos);
    }

    @Inject(method = {"interactEntity"}, at = @At("HEAD"))
    private void cera$rememberEntity(PlayerEntity player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft.getInstance().cera$getCustomGuis().interactedWith(entity);
    }

    @Inject(method = "interactEntityAt", at = @At("HEAD"))
    private void cera$rememberEntityAt(PlayerEntity player, Entity entity, HitResult hit, CallbackInfoReturnable<Boolean> cir) {
        Minecraft.getInstance().cera$getCustomGuis().interactedWith(entity);
    }
}
