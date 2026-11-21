package hack.skate.client.features.impl.render;

import hack.skate.client.Skate;
import hack.skate.client.config.ColorConfig;
import hack.skate.client.event.Subscribe;
import hack.skate.client.event.impl.EventRender2D;
import hack.skate.client.features.Category;
import hack.skate.client.features.Feature;
import hack.skate.client.features.FeatureInfo;
import hack.skate.client.features.settings.impl.BoolSetting;
import hack.skate.client.features.settings.impl.IntSetting;
import hack.skate.client.features.settings.impl.ModeSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@FeatureInfo(name = "HUD", description = "Stuffs on ur screen", category = Category.RENDER)
public class HUD extends Feature {
    private final ModeSetting theme = new ModeSetting("Theme", "Skate", "Skate", "Ocean", "Sakura", "Monochrome");
    private final BoolSetting watermark = new BoolSetting("Watermark", true);
    private final IntSetting watermarkX = new IntSetting("Watermark X", 5, 0, 500, o -> watermark.getValue());
    private final IntSetting watermarkY = new IntSetting("Watermark Y", 5, 0, 500, o -> watermark.getValue());
    private final BoolSetting arrayList = new BoolSetting("ArrayList", true);
    private final IntSetting arrayListOffset = new IntSetting("List Offset", 5, 0, 500, o -> arrayList.getValue());

    private String getThemeName() {
        return String.valueOf(theme.getValue());
    }

    private int getAccentColor() {
        return switch (getThemeName()) {
            case "Ocean" -> 0xFF38BDF8;
            case "Sakura" -> 0xFFF472B6;
            case "Monochrome" -> 0xFFE5E7EB;
            default -> ColorConfig.PRIMARY_ACCENT.getRGB();
        };
    }

    private int getTextColor() {
        return switch (getThemeName()) {
            case "Ocean" -> 0xFFE0F2FE;
            case "Sakura" -> 0xFFFCE7F3;
            case "Monochrome" -> 0xFFFFFFFF;
            default -> ColorConfig.TEXT_WHITE.getRGB();
        };
    }

    private int getPanelColor() {
        return switch (getThemeName()) {
            case "Ocean" -> 0x90082F49;
            case "Sakura" -> 0x904A0D27;
            case "Monochrome" -> 0x9018181B;
            default -> 0x90121216;
        };
    }

    @Subscribe
    public void onRender2D(EventRender2D event) {
        if (mc.getDebugHud().shouldShowDebugHud()) return;

        int accentColor = getAccentColor();
        int textColor = getTextColor();
        int panelColor = getPanelColor();

        if (watermark.getValue()) {
            String prefix = "S";
            String suffix = "kate";

            int wx = watermarkX.getValue();
            int wy = watermarkY.getValue();
            float prefixWidth = Skate.fonts.getArial().getWidth(prefix, 9);
            float suffixWidth = Skate.fonts.getArial().getWidth(suffix, 9);

            event.getContext().fill(
                    wx - 3,
                    wy - 2,
                    (int) (wx + prefixWidth + suffixWidth + 3),
                    wy + 11,
                    panelColor
            );
            event.getContext().fill(
                    wx - 3,
                    wy - 2,
                    (int) (wx + prefixWidth + suffixWidth + 3),
                    wy - 1,
                    accentColor
            );

            Skate.fonts.getArial().renderWithShadow(
                    event.getMatrixStack(),
                    prefix,
                    wx,
                    wy,
                    9,
                    accentColor
            );

            Skate.fonts.getArial().renderWithShadow(
                    event.getMatrixStack(),
                    suffix,
                    wx + prefixWidth - 1,
                    wy,
                    9,
                    textColor
            );
        }

        if (arrayList.getValue()) {
            List<Feature> enabledFeatures = new ArrayList<>();
            for (Feature feature : Skate.featureManager.getFeatures()) {
                if (feature.isEnabled() && feature != this) {
                    enabledFeatures.add(feature);
                }
            }

            enabledFeatures.sort(Comparator.comparingDouble(f ->
                    -Skate.fonts.getArial().getWidth(getDisplayText(f), 8)));

            int screenWidth = mc.getWindow().getScaledWidth();
            int offset = arrayListOffset.getValue();
            int y = offset;

            for (Feature feature : enabledFeatures) {
                String displayText = getDisplayText(feature);
                float textWidth = Skate.fonts.getArial().getWidth(displayText, 8);
                int x = (int) (screenWidth - textWidth - offset);

                event.getContext().fill(
                        x - 3,
                        y - 1,
                        screenWidth - offset + 2,
                        y + 9,
                        panelColor
                );
                event.getContext().fill(
                        screenWidth - offset + 1,
                        y - 1,
                        screenWidth - offset + 2,
                        y + 9,
                        accentColor
                );

                Skate.fonts.getArial().renderWithShadow(
                        event.getMatrixStack(),
                        displayText,
                        x,
                        y,
                        8,
                        accentColor
                );

                y += 10;
            }
        }
    }

    private String getDisplayText(Feature feature) {
        String info = feature.getInfo();
        if (info != null && !info.isEmpty()) {
            return feature.getName() + " \u00A77" + info;
        }
        return feature.getName();
    }
}
