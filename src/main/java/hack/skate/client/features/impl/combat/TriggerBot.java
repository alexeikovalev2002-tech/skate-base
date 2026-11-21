package hack.skate.client.features.impl.combat;

import hack.skate.client.Skate;
import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventTick;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import hack.skate.client.features.impl.client.Teams;
import hack.skate.client.features.settings.impl.BoolSetting;
import hack.skate.client.features.settings.impl.FloatSetting;
import hack.skate.client.features.settings.impl.IntSetting;
import hack.skate.client.features.settings.impl.ModeSetting;
import hack.skate.client.features.settings.impl.RangeSetting;
import hack.skate.client.utils.PlayerUtil;
import hack.skate.client.utils.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

@FeatureInfo(name = "TriggerBot", description = "Auto hits people.", category = Category.COMBAT)
public class TriggerBot extends Feature {
    public RangeSetting attackDelay = new RangeSetting("Delay", 100.0f, 150.0f, 0.0f, 1000.0f, 1.0f, " ms");
    public FloatSetting range = new FloatSetting("Range", 4.5f, 1f, 6f, 0.1f);
    public IntSetting chance = new IntSetting("Chance", 80, 0, 100, "%");
    public BoolSetting preferCrits = new BoolSetting("Prefer crits", true);
    public BoolSetting weapon = new BoolSetting("Need weapon", true);
    public BoolSetting mouse = new BoolSetting("Mouse", false);
    public ModeSetting mouseButton = new ModeSetting("Mouse button", "LMB", o -> mouse.getValue(), "LMB", "RMB", "MMB");
    public BoolSetting players = new BoolSetting("Players", true);
    public BoolSetting mobs = new BoolSetting("Mobs", false);
    public BoolSetting animals = new BoolSetting("Animals", false);

    private final Timer attackTimer = new Timer();
    private float currentDelay;
    private final Timer targetTimeout = new Timer();

    public TriggerBot() {
        this.currentDelay = randomizeDelay();
    }

    private float randomizeDelay() {
        float min = attackDelay.getMinValue();
        float max = Math.max(min, attackDelay.getMaxValue());
        return (float) (Math.random() * (max - min) + min);
    }

    @Override
    public void onEnable() {
        currentDelay = randomizeDelay();
        super.onEnable();
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        if (!isMouseConditionMet()) {
            return;
        }

        Entity target = getTargetedEntity();
        if (target == null || !target.isAlive() || !target.isAttackable()) {
            return;
        }

        if (!shouldTarget(target)) {
            return;
        }

        if (mc.player.distanceTo(target) > range.getValue()) {
            return;
        }

        if (weapon.getValue() && !hasAllowedWeapon()) {
            return;
        }

        if (preferCrits.getValue() && !PlayerUtil.canCrit()) {
            if (!mc.player.isOnGround() && mc.player.getVelocity().y > 0.0) {
                return;
            }
        }

        if (mc.player.getAttackCooldownProgress(0.5f) >= 1.0f
                && attackTimer.passedMs((long) currentDelay)
                && chance.getValue() > (int) (Math.random() * 100)) {

            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            targetTimeout.reset();
            attackTimer.reset();
            currentDelay = randomizeDelay();
        }
    }

    private Entity getTargetedEntity() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (entity != null) {
                return entity;
            }
        }
        return mc.targetedEntity;
    }

    private boolean isMouseConditionMet() {
        if (!mouse.getValue()) {
            return true;
        }
        long handle = mc.getWindow().getHandle();
        String selectedButton = String.valueOf(mouseButton.getValue());
        int glfwButton = switch (selectedButton) {
            case "RMB" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "MMB" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
        };
        return GLFW.glfwGetMouseButton(handle, glfwButton) == GLFW.GLFW_PRESS;
    }

    private boolean hasAllowedWeapon() {
        if (mc.player == null || mc.player.getMainHandStack() == null || mc.player.getMainHandStack().isEmpty()) {
            return false;
        }
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof PickaxeItem;
    }

    private boolean shouldTarget(Entity entity) {
        if (entity instanceof PlayerEntity && players.getValue()) {
            Teams teams = Skate.featureManager.getFeatureByClass(Teams.class);
            if (teams != null && teams.isEnabled() && teams.isOnSameTeam(entity)) {
                return false;
            }
            return true;
        }
        if (entity instanceof HostileEntity && mobs.getValue()) return true;
        if (entity instanceof PassiveEntity && animals.getValue()) return true;
        return false;
    }

    @Override
    public String getInfo() {
        return Math.round(attackDelay.getMinValue()) + "ms";
    }
}
