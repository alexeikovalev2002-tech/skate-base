package hack.skate.client.mixin;

import hack.skate.client.features.FeatureManager;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            )
    )
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

        FeatureManager featureManager = resolveFeatureManager();
        if (featureManager == null) {
            return;
        }

        CustomAnimation customAnimation = featureManager.getFeatureByClass(CustomAnimation.class);
        if (customAnimation != null && customAnimation.isEnabled()) {
            Arm arm = hand == Hand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            boolean isLeftHand = arm == Arm.LEFT;
            customAnimation.applyAnimation(matrices, swingProgress, isLeftHand);
        }
    }

    private FeatureManager resolveFeatureManager() {
        String[] candidateClassNames = {
                "hack.skate.client.SkateClient",
                "hack.skate.client.Skate",
                "hack.skate.client.Client"
        };

        for (String className : candidateClassNames) {
            try {
                Class<?> clazz = Class.forName(className);
                Object instance = null;

                for (String methodName : new String[]{"getInstance", "instance"}) {
                    try {
                        Method m = clazz.getMethod(methodName);
                        instance = m.invoke(null);
                        if (instance != null) break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                if (instance == null) {
                    for (Field f : clazz.getDeclaredFields()) {
                        f.setAccessible(true);
                        if (clazz.isAssignableFrom(f.getType())) {
                            instance = f.get(null);
                            if (instance != null) break;
                        }
                    }
                }

                if (instance != null) {
                    for (Method m : instance.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && FeatureManager.class.isAssignableFrom(m.getReturnType())) {
                            Object fm = m.invoke(instance);
                            if (fm instanceof FeatureManager) {
                                return (FeatureManager) fm;
                            }
                        }
                    }
                    for (Field f : instance.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        if (FeatureManager.class.isAssignableFrom(f.getType())) {
                            Object fm = f.get(instance);
                            if (fm instanceof FeatureManager) {
                                return (FeatureManager) fm;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
