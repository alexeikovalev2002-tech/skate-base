package hack.skate.client.features.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventTick;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import hack.skate.client.features.settings.impl.FloatSetting;
import hack.skate.client.features.settings.impl.IntSetting;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FeatureInfo(name = "JumpCircle", description = "Draws a circle under you when you jump", category = Category.RENDER)
public class JumpCircle extends Feature {
    public FloatSetting maxRadius = new FloatSetting("Max Radius", 1.5f, 0.5f, 4.0f, 0.1f);
    public IntSetting durationMs = new IntSetting("Duration", 800, 200, 3000, " ms");
    public IntSetting segments = new IntSetting("Segments", 36, 12, 72);

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
        boolean jumped = wasOnGround && !onGround && (mc.player.getVelocity().y > 0.05 || mc.player.getY() > lastY);

        if (jumped) {
            Vec3d pos = mc.player.getPos();
            circles.add(new CircleInstance(new Vec3d(pos.x, pos.y + 0.02, pos.z), System.currentTimeMillis()));
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

    // Если в вашей базе есть EventRender3D, повесьте @Subscribe на метод с ним и вызовите renderCircles(event.getMatrixStack(), tickDelta)
    public void renderCircles(MatrixStack matrices, float tickDelta) {
        if (circles.isEmpty() || mc.player == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        long now = System.currentTimeMillis();
        int lifeTime = Math.max(1, durationMs.getValue());
        int segs = Math.max(12, segments.getValue());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        for (CircleInstance circle : circles) {
            float progress = Math.min(1.0f, (now - circle.startTime) / (float) lifeTime);
            float radius = maxRadius.getValue() * (0.25f + 0.75f * progress);
            float alpha = 1.0f - progress;

            int a = Math.max(0, Math.min(255, (int) (alpha * 220)));
            int r = 85;
            int g = 220;
            int b = 255;

            for (int i = 0; i <= segs; i++) {
                double angle = (2.0 * Math.PI * i) / segs;
                float x = (float) (circle.pos.x + Math.cos(angle) * radius);
                float y = (float) circle.pos.y;
                float z = (float) (circle.pos.z + Math.sin(angle) * radius);
                buffer.vertex(matrix, x, y, z).color(r, g, b, a);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        matrices.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
