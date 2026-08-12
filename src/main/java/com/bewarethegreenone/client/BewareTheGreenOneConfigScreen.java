package com.bewarethegreenone.client;

import com.bewarethegreenone.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BewareTheGreenOneConfigScreen extends Screen {

    private final Screen parent;

    private Button positionButton;
    private Button visibilityButton;

    public BewareTheGreenOneConfigScreen(Screen parent) {
        super(Component.literal("Beware the Green One"));
        this.parent = parent;
    }

    @Override
    protected void init() {

        // HUD表示/非表示ボタン
        visibilityButton = Button.builder(
                getVisibilityText(),
                button -> {

                    Config.setHudVisible(!Config.hudVisible);

                    button.setMessage(
                            getVisibilityText()
                    );
                }
        ).bounds(
                this.width / 2 - 100,
                this.height / 2 - 40,
                200,
                20
        ).build();

        this.addRenderableWidget(visibilityButton);


        // HUD位置ボタン
        positionButton = Button.builder(
                getPositionText(),
                button -> {

                    int next =
                            (Config.hudPosition + 1) % 4;

                    Config.setHudPosition(next);

                    button.setMessage(
                            getPositionText()
                    );
                }
        ).bounds(
                this.width / 2 - 100,
                this.height / 2 - 10,
                200,
                20
        ).build();

        this.addRenderableWidget(positionButton);


        // Doneボタン
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("Done"),
                        button ->
                                this.minecraft.setScreen(parent)
                ).bounds(
                        this.width / 2 - 100,
                        this.height / 2 + 25,
                        200,
                        20
                ).build()
        );
    }

    private Component getVisibilityText() {

        return Component.literal(
                "HUD: "
                        + (Config.hudVisible ? "ON" : "OFF")
        );
    }

    private Component getPositionText() {

        String position;

        switch (Config.hudPosition) {

            case 0 ->
                    position = "Top Right";

            case 1 ->
                    position = "Top Left";

            case 2 ->
                    position = "Bottom Right";

            case 3 ->
                    position = "Bottom Left";

            default ->
                    position = "Top Right";
        }

        return Component.literal(
                "HUD Position: " + position
        );
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        this.renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                30,
                0xFFFFFF
        );

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}