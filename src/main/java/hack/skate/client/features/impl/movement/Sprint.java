package hack.skate.client.features.impl.movement;

import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventMove;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import net.minecraft.entity.effect.StatusEffects;

@FeatureInfo(name = "Sprint", description = "Automatically sprints when moving forward", category = Category.MOVEMENT)
public class Sprint extends Feature {

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
        if (mc.options != null && mc.options.sprintKey != null) {
            mc.options.sprintKey.setPressed(false);
        }
        super.onDisable();
    }

    @Subscribe
    public void onMove(EventMove event) {
        if (mc.player == null || mc.options == null) return;

        boolean canSprint = mc.player.input != null
                && mc.player.input.movementForward > 0.0f
                && !mc.player.horizontalCollision
                && !mc.player.isSneaking()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && mc.player.getHungerManager().getFoodLevel() > 6;

        if (canSprint) {
            mc.player.setSprinting(true);
        }
    }
}
