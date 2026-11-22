package hack.skate.client.features.impl.render;

import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventTick;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import hack.skate.client.features.settings.impl.BoolSetting;
import hack.skate.client.features.settings.impl.FloatSetting;
import hack.skate.client.features.settings.impl.ModeSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

@FeatureInfo(
        name = "CustomAnimation",
        description = "Customize hand/item position and swing animations",
        category = Category.RENDER
)
public class CustomAnimation extends Feature {
    public ModeSetting animation = new ModeSetting(
            "Animation",
            "Smooth",
            "Default",
            "Swing",
            "Spin",
            "Punch",
            "Smooth"
    );

    public FloatSetting handX = new FloatSetting("Hand X", 0.0f, -1.5f, 1.5f, 0.05f);
    public FloatSetting handY = new FloatSetting("Hand Y", 0.0f, -1.5f, 1.5f, 0.05f);
    public FloatSetting itemX = new FloatSetting("Item X", 0.0f, -1.5f, 1.5f, 0.05f);
    public FloatSetting itemY = new FloatSetting("Item Y", 0.0f, -1.5f, 1.5f, 0.05f);
    public FloatSetting itemZ = new FloatSetting("Item Z", 0.0f, -1.5f, 1.5f, 0.05f);
    public FloatSetting speed = new FloatSetting("Speed", 1.0f, 0.2f, 3.0f, 0.1f);
    public BoolSetting onlyWhenSwinging = new BoolSetting("Only Swing", false);

    private float animProgress = 0.0f;

    @Override
    public void onEnable() {
        animProgress = 0.0f;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        animProgress = 0.0f;
        super.onDisable();
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (!isEnabled() || mc.player == null) {
            animProgress = 0.0f;
            return;
        }

        float step = 0.08f * speed.getValue();
        if (mc.player.handSwinging) {
            animProgress = Math.min(1.0f, animProgress + step * 1.6f);
        } else {
            animProgress = Math.max(0.0f, animProgress - step);
        }
    }

    public void applyAnimation(MatrixStack matrices, float swingProg, boolean isOffHand) {
        if (!isEnabled() || matrices == null) return;

        int side = isOffHand ? -1 : 1;
        float clampedSwing = Math.max(0.0f, Math.min(1.0f, swingProg));
        float activeFactor = onlyWhenSwinging.getValue()
                ? Math.max(clampedSwing, animProgress)
                : 1.0f;

        matrices.translate(handX.getValue() * side, handY.getValue(), 0.0f);
        matrices.translate(itemX.getValue() * side, itemY.getValue(), itemZ.getValue());

        String mode = String.valueOf(animation.getValue());
        float t = clampedSwing > 0.0f ? clampedSwing : animProgress;

        switch (mode) {
            case "Swing" -> {
                float angle = (float) Math.sin(t * Math.PI) * 55.0f * activeFactor;
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-angle));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle * 0.45f * side));
            }
            case "Spin" -> {
                float spinAngle = t * 360.0f * activeFactor;
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle * side));
            }
            case "Punch" -> {
                float thrust = (float) Math.sin(t * Math.PI) * activeFactor;
                matrices.translate(0.0f, thrust * 0.12f, -thrust * 0.28f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-thrust * 40.0f));
            }
            case "Smooth" -> {
                float smooth = t * t * (3.0f - 2.0f * t) * activeFactor;
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-smooth * 38.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(smooth * 25.0f * side));
            }
            default -> {
            }
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(animation.getValue());
    }
}
