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

    private int cachedSegs = -1;
    private float[] cosTable = new float[0];
    private float[] sinTable = new float[0];

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
                    pos.x,
                    pos.y + yOffset.getValue(),
                    pos.z,
                    System.currentTimeMillis()
            ));
        }

        wasOnGround = onGround;
        lastY = mc.player.getY();

        if (!circles.isEmpty()) {
            long now = System.currentTimeMillis();
            int maxAge = durationMs.getValue();
            Iterator<CircleInstance> it = circles.iterator();
            while (it.hasNext()) {
                if (now - it.next().startTime >= maxAge) {
                    it.remove();
                }
            }
        }
    }

    private void ensureTrigTables(int segs) {
        if (segs == cachedSegs && cosTable.length == segs + 1) return;
        cachedSegs = segs;
        cosTable = new float[segs + 1];
        sinTable = new float[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double angle = (2.0 * Math.PI * i) / segs;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
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
        ensureTrigTables(segs);

        String currentMode = String.valueOf(mode.getValue());
        String theme = String.valueOf(colorMode.getValue());
        boolean drawFill = fill.getValue() || "Disc".equals(currentMode);
        boolean isDoubleRing = "Double Ring".equals(currentMode);
        boolean isSegmented = "Segmented".equals(currentMode);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        matrices.push();
        try {
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();

            for (int cIdx = 0; cIdx < circles.size(); cIdx++) {
                CircleInstance circle = circles.get(cIdx);
                float rawProgress = Math.min(1.0f, Math.max(0.0f, (now - circle.startTime) / (float) lifeTime));
                float inv = 1.0f - rawProgress;
                float eased = 1.0f - inv * inv * inv;
                float outerRadius = maxRadius.getValue() * (0.2f + 0.8f * eased);
                float alphaFactor = 1.0f - (float) Math.pow(rawProgress, 1.6);

                float cx = (float) circle.x;
                float cy = (float) circle.y;
                float cz = (float) circle.z;

                if (drawFill) {
                    float bandWidth = Math.min(outerRadius * 0.85f, thickness.getValue() * (0.5f + 0.5f * eased));
                    float innerRadius = Math.max(0.0f, outerRadius - bandWidth);

                    BufferBuilder fanBuffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                    for (int i = 0; i <= segs; i++) {
                        float cos = cosTable[i];
                        float sin = sinTable[i];
                        double angle = (2.0 * Math.PI * i) / segs;

                        int edgeRgba = computeRgba(theme, rawProgress, angle, alphaFactor, 165);
                        int innerRgba = computeRgba(theme, rawProgress, angle, alphaFactor, 15);

                        fanBuffer.vertex(matrix, cx + cos * outerRadius, cy, cz + sin * outerRadius)
                                .color(edgeRgba);
                        fanBuffer.vertex(matrix, cx + cos * innerRadius, cy, cz + sin * innerRadius)
                                .color(innerRgba);
                    }
                    BufferRenderer.drawWithGlobalProgram(fanBuffer.end());
                }

                if (isDoubleRing) {
                    drawRing(tessellator, matrix, cx, cy, cz, outerRadius, rawProgress, alphaFactor, segs, theme, 235);
                    drawRing(tessellator, matrix, cx, cy, cz, outerRadius * 0.65f, rawProgress, alphaFactor * 0.75f, segs, theme, 190);
                } else if (isSegmented) {
                    drawSegmentedRing(tessellator, matrix, cx, cy, cz, outerRadius, rawProgress, alphaFactor, segs, theme, 235);
                } else {
                    drawRing(tessellator, matrix, cx, cy, cz, outerRadius, rawProgress, alphaFactor, segs, theme, 240);
                }
            }
        } finally {
            matrices.pop();
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private void drawRing(Tessellator tessellator, Matrix4f matrix, float cx, float cy, float cz,
                          float radius, float progress, float alphaFactor, int segs, String theme, int baseAlpha) {
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double angle = (2.0 * Math.PI * i) / segs;
            int rgba = computeRgba(theme, progress, angle, alphaFactor, baseAlpha);
            lineBuffer.vertex(matrix, cx + cosTable[i] * radius, cy, cz + sinTable[i] * radius)
                    .color(rgba);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
    }

    private void drawSegmentedRing(Tessellator tessellator, Matrix4f matrix, float cx, float cy, float cz,
                                   float radius, float progress, float alphaFactor, int segs, String theme, int baseAlpha) {
        int dashCount = 6;
        float spin = progress * (float) (Math.PI * 1.5);
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segs; i++) {
            int sector = (int) (((double) i / segs) * dashCount * 2);
            if (sector % 2 == 0) {
                double a1 = (2.0 * Math.PI * i) / segs + spin;
                double a2 = (2.0 * Math.PI * (i + 1)) / segs + spin;
                int rgba = computeRgba(theme, progress, a1, alphaFactor, baseAlpha);

                lineBuffer.vertex(matrix, cx + (float) Math.cos(a1) * radius, cy, cz + (float) Math.sin(a1) * radius)
                        .color(rgba);
                lineBuffer.vertex(matrix, cx + (float) Math.cos(a2) * radius, cy, cz + (float) Math.sin(a2) * radius)
                        .color(rgba);
            }
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
    }

    private int computeRgba(String theme, float progress, double angle, float alphaFactor, int baseAlpha) {
        int r;
        int g;
        int b;

        switch (theme) {
            case "Sakura" -> {
                r = 255;
                g = 155;
                b = 195;
            }
            case "Ocean" -> {
                r = 60;
                g = 145;
                b = 255;
            }
            case "Rainbow" -> {
                float hue = (float) ((angle / (2.0 * Math.PI)) + progress * 0.5f) % 1.0f;
                int rgb = Color.HSBtoRGB(hue, 0.75f, 1.0f);
                r = (rgb >> 16) & 0xFF;
                g = (rgb >> 8) & 0xFF;
                b = rgb & 0xFF;
            }
            default -> {
                r = 85;
                g = 225;
                b = 255;
            }
        }

        int a = Math.max(0, Math.min(255, (int) (baseAlpha * alphaFactor)));
        return ((r & 0xFF) << 24) | ((g & 0xFF) << 16) | ((b & 0xFF) << 8) | (a & 0xFF);
    }

    @Override
    public String getInfo() {
        return String.valueOf(mode.getValue());
    }

    private static final class CircleInstance {
        private final double x;
        private final double y;
        private final double z;
        private final long startTime;

        private CircleInstance(double x, double y, double z, long startTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startTime = startTime;
        }
    }
}
