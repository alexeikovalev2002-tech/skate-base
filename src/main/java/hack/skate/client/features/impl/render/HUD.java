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

                Skate.fonts.getArial().renderWithShadow(
                        event.getMatrixStack(),
                        displayText,
                        x,
                        y,
                        8,
                        ColorConfig.PRIMARY_ACCENT.getRGB()
                );

                y += 10;
            }
        }
