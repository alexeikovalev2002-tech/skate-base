package hack.skate.client.mixin;

import hack.skate.client.features.impl.render.CustomAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void skate$customAnimation(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (this.client == null || this.client.player != player) {
            return;
        }

        CustomAnimation customAnimation = CustomAnimation.getInstance();
        if (customAnimation != null && customAnimation.isEnabled()) {
            Arm arm = hand == Hand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            boolean isLeftHand = arm == Arm.LEFT;
            customAnimation.applyAnimation(matrices, swingProgress, isLeftHand);
        }
    }
}
