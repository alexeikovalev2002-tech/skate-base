package hack.skate.client.features.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventRender3D;
import hack.skate.client.event.impl.EventTick;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import hack.skate.client.features.settings.impl.BoolSetting;
import hack.skate.client.features.settings.impl.FloatSetting;
import hack.skate.client.features.settings.impl.IntSetting;
import hack.skate.client.features.settings.impl.ModeSetting;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FeatureInfo(name = "JumpCircle", description = "Draws stylized circles under you when you jump", category = Category.RENDER)
public class JumpCircle extends Feature {
    public ModeSetting mode = new ModeSetting("Mode", "Disc", "Ring", "Double Ring", "Disc", "Segmented");
    public ModeSetting colorMode = new ModeSetting("Color", "Sakura", "Cyan", "Sakura", "Ocean", "Rainbow");
    public FloatSetting maxRadius = new FloatSetting("Max Radius", 1.6f, 0.5f, 4.5f, 0.1f);
    public FloatSetting thickness = new FloatSetting("Thickness", 0.35f, 0.05f, 1.2f, 0.05f);
    public FloatSetting yOffset = new FloatSetting("Y Offset", 0.03f, 0.01f, 0.25f, 0.01f);
    public IntSetting durationMs = new IntSetting("Duration", 850, 200, 3000, " ms");
    public IntSetting segments = new IntSetting("Segments", 48, 16, 96);
    public BoolSetting fill = new BoolSetting("Fill", true);

    private final List<CircleInstance> circles = new ArrayList<>();
    private boolean wasOnGround = true;
    private double lastY = 0.0;

    @Override
    public void onEnable() {
        circles.clear();
        if (mc.player != null) {
            wasOnGround = mc.player.isOnGround();
            lastY = mc.player.getY();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        circles.clear();
        super.onDisable();
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) {
            circles.clear();
            return;
        }

        boolean onGround = mc.player.isOnGround();
        boolean jumpPressed = mc.options != null && mc.options.jumpKey.isPressed();
        boolean movedUp = mc.player.getVelocity().y > 0.01 || mc.player.getY() > lastY + 0.005;
        boolean jumped = wasOnGround && !onGround && (movedUp || jumpPressed);

        if (jumped) {
            Vec3d pos = mc.player.getPos();
            circles.add(new CircleInstance(
                    new Vec3d(pos.x, pos.y + yOffset.getValue(), pos.z),
                    System.currentTimeMillis()
            ));
        }

        wasOnGround = onGround;
        lastY = mc.player.getY();

        long now = System.currentTimeMillis();
        Iterator<CircleInstance> it = circles.iterator();
        while (it.hasNext()) {
            CircleInstance c = it.next();
            if (now - c.startTime >= durationMs.getValue()) {
                it.remove();
            }
        }
    }

    @Subscribe
    public void onRender3D(EventRender3D event) {
        if (circles.isEmpty() || mc.player == null) return;

        MatrixStack matrices = event.getMatrices();
        if (matrices == null) return;

        Vec3d camPos = event.getCamera() != null
                ? event.getCamera().getPos()
                : (mc.gameRenderer != null ? mc.gameRenderer.getCamera().getPos() : Vec3d.ZERO);

        long now = System.currentTimeMillis();
        int lifeTime = Math.max(1, durationMs.getValue());
        int segs = Math.max(16, segments.getValue());
        String currentMode = String.valueOf(mode.getValue());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Tessellator tessellator = Tessellator.getInstance();

        for (CircleInstance circle : circles) {
            float rawProgress = Math.min(1.0f, Math.max(0.0f, (now - circle.startTime) / (float) lifeTime));
            // Плавное замедление расширения (ease-out cubic)
            float eased = 1.0f - (float) Math.pow(1.0f - rawProgress, 3.0);
            float outerRadius = maxRadius.getValue() * (0.2f + 0.8f * eased);
            float alphaFactor = 1.0f - (float) Math.pow(rawProgress, 1.6);

            if (fill.getValue() || "Disc".equals(currentMode)) {
                float bandWidth = Math.min(outerRadius * 0.85f, thickness.getValue() * (0.5f + 0.5f * eased));
                float innerRadius = Math.max(0.0f, outerRadius - bandWidth);

                BufferBuilder fanBuffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                for (int i = 0; i <= segs; i++) {
                    double angle = (2.0 * Math.PI * i) / segs;
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);

                    Color edgeColor = getThemeColor(rawProgress, angle, alphaFactor, 165);
                    Color innerColor = getThemeColor(rawProgress, angle, alphaFactor, 15);

                    float ox = (float) circle.pos.x + cos * outerRadius;
                    float oz = (float) circle.pos.z + sin * outerRadius;
                    float ix = (float) circle.pos.x + cos * innerRadius;
                    float iz = (float) circle.pos.z + sin * innerRadius;
                    float y = (float) circle.pos.y;

                    fanBuffer.vertex(matrix, ox, y, oz).color(edgeColor.getRed(), edgeColor.getGreen(), edgeColor.getBlue(), edgeColor.getAlpha());
                    fanBuffer.vertex(matrix, ix, y, iz).color(innerColor.getRed(), innerColor.getGreen(), innerColor.getBlue(), innerColor.getAlpha());
                }
                BufferRenderer.drawWithGlobalProgram(fanBuffer.end());
            }

            if ("Double Ring".equals(currentMode)) {
                drawRing(tessellator, matrix, circle.pos, outerRadius, rawProgress, alphaFactor, segs, 235, 0.0);
                float secondRadius = outerRadius * 0.65f;
                drawRing(tessellator, matrix, circle.pos, secondRadius, rawProgress, alphaFactor * 0.75f, segs, 190, 0.0);
            } else if ("Segmented".equals(currentMode)) {
                drawSegmentedRing(tessellator, matrix, circle.pos, outerRadius, rawProgress, alphaFactor, segs, 235);
            } else {
                drawRing(tessellator, matrix, circle.pos, outerRadius, rawProgress, alphaFactor, segs, 240, 0.0);
            }
        }

        matrices.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawRing(Tessellator tessellator, Matrix4f matrix, Vec3d center, float radius,
                          float progress, float alphaFactor, int segs, int baseAlpha, double rotationOffset) {
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double angle = (2.0 * Math.PI * i) / segs + rotationOffset;
            float x = (float) (center.x + Math.cos(angle) * radius);
            float y = (float) center.y;
            float z = (float) (center.z + Math.sin(angle) * radius);

            Color c = getThemeColor(progress, angle, alphaFactor, baseAlpha);
            lineBuffer.vertex(matrix, x, y, z).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
    }

    private void drawSegmentedRing(Tessellator tessellator, Matrix4f matrix, Vec3d center, float radius,
                                   float progress, float alphaFactor, int segs, int baseAlpha) {
        int dashCount = 6;
        float spin = progress * (float) (Math.PI * 1.5);
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segs; i++) {
            double a1 = (2.0 * Math.PI * i) / segs + spin;
            double a2 = (2.0 * Math.PI * (i + 1)) / segs + spin;

            int sector = (int) (((double) i / segs) * dashCount * 2);
            if (sector % 2 == 0) {
                Color c = getThemeColor(progress, a1, alphaFactor, baseAlpha);
                float x1 = (float) (center.x + Math.cos(a1) * radius);
                float z1 = (float) (center.z + Math.sin(a1) * radius);
                float x2 = (float) (center.x + Math.cos(a2) * radius);
                float z2 = (float) (center.z + Math.sin(a2) * radius);
                float y = (float) center.y;

                lineBuffer.vertex(matrix, x1, y, z1).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
                lineBuffer.vertex(matrix, x2, y, z2).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
            }
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
    }

    private Color getThemeColor(float progress, double angle, float alphaFactor, int baseAlpha) {
        String theme = String.valueOf(colorMode.getValue());
        int alpha = Math.max(0, Math.min(255, (int) (baseAlpha * alphaFactor)));

        Color base;
        switch (theme) {
            case "Sakura" -> base = new Color(255, 155, 195);
            case "Ocean" -> base = new Color(60, 145, 255);
            case "Rainbow" -> {
                float hue = (float) ((angle / (2.0 * Math.PI)) + progress * 0.5f) % 1.0f;
                base = Color.getHSBColor(hue, 0.75f, 1.0f);
            }
            default -> base = new Color(85, 225, 255);
        }

        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    @Override
    public String getInfo() {
        return String.valueOf(mode.getValue());
    }

    private static class CircleInstance {
        private final Vec3d pos;
        private final long startTime;

        private CircleInstance(Vec3d pos, long startTime) {
            this.pos = pos;
            this.startTime = startTime;
        }
    }
}
