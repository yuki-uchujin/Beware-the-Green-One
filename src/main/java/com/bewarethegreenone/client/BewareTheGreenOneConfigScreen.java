package com.bewarethegreenone.client;

import com.bewarethegreenone.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BewareTheGreenOneConfigScreen extends Screen {

    private final Screen parent;

    private Button positionButton;
    private Button visibilityButton;
    private Button explosionModeButton;
    private EditBox maxMultiplierBox;

    public BewareTheGreenOneConfigScreen(Screen parent) {

        super(Component.translatable(
                "bewarethegreenone.config.title"
        ));

        this.parent = parent;
    }


    @Override
    protected void init() {

        // =========================
        // レイアウト位置
        // =========================

        int leftColumnX =
                this.width / 2 - 105;

        int rightColumnX =
                this.width / 2 + 35;

        int topY =
                this.height / 2 - 60;


        // =========================
        // HUD表示/非表示
        // =========================

        visibilityButton = Button.builder(
                getVisibilityText(),
                button -> {

                    Config.setHudVisible(
                            !Config.hudVisible
                    );

                    button.setMessage(
                            getVisibilityText()
                    );
                }
        ).bounds(
                leftColumnX,
                topY,
                100,
                20
        ).build();

        this.addRenderableWidget(
                visibilityButton
        );


        // =========================
        // 最大倍率入力欄
        // =========================

        maxMultiplierBox = new EditBox(
                this.font,
                rightColumnX,
                topY + 30,
                80,
                20,
                Component.translatable(
                        "bewarethegreenone.config.max_multiplier"
                )
        );

        maxMultiplierBox.setValue(
                String.valueOf(
                        Config.maxExplosionMultiplier
                )
        );

        maxMultiplierBox.setFilter(
                text -> text.matches(
                        "\\d*(\\.\\d*)?"
                )
        );

        this.addRenderableWidget(
                maxMultiplierBox
        );


        // =========================
        // HUD位置
        // =========================

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
                leftColumnX,
                topY + 30,
                100,
                20
        ).build();

        this.addRenderableWidget(
                positionButton
        );


        // =========================
        // 爆発モード
        // =========================

        explosionModeButton = Button.builder(
                getExplosionModeText(),
                button -> {

                    int next =
                            (Config.explosionMode + 1) % 2;

                    Config.setExplosionMode(next);

                    button.setMessage(
                            getExplosionModeText()
                    );
                }
        ).bounds(
                leftColumnX,
                topY + 60,
                100,
                20
        ).build();

        this.addRenderableWidget(
                explosionModeButton
        );


        // =========================
        // Doneボタン
        // =========================

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable(
                                "bewarethegreenone.config.done"
                        ),
                        button -> {

                            saveMaxMultiplier();

                            this.minecraft.setScreen(
                                    parent
                            );
                        }
                ).bounds(
                        this.width / 2 - 50,
                        topY + 100,
                        100,
                        20
                ).build()
        );
    }


    // =========================
    // 最大倍率を保存
    // =========================

    private void saveMaxMultiplier() {

        String text =
                maxMultiplierBox.getValue();

        try {

            double value =
                    Double.parseDouble(text);

            if (value >= 1.0) {

                Config.setMaxExplosionMultiplier(
                        value
                );
            }

        } catch (NumberFormatException ignored) {

            // 不正な値の場合は現在の設定を維持
        }
    }


    // =========================
    // HUD表示/非表示
    // =========================

    private Component getVisibilityText() {

        return Component.translatable(
                "bewarethegreenone.config.hud",
                Component.translatable(
                        Config.hudVisible
                                ? "bewarethegreenone.config.on"
                                : "bewarethegreenone.config.off"
                )
        );
    }


    // =========================
    // HUD位置
    // =========================

    private Component getPositionText() {

        String positionKey;

        switch (Config.hudPosition) {

            case 0 ->
                    positionKey =
                            "bewarethegreenone.config.position.top_right";

            case 1 ->
                    positionKey =
                            "bewarethegreenone.config.position.top_left";

            case 2 ->
                    positionKey =
                            "bewarethegreenone.config.position.bottom_right";

            case 3 ->
                    positionKey =
                            "bewarethegreenone.config.position.bottom_left";

            default ->
                    positionKey =
                            "bewarethegreenone.config.position.top_right";
        }

        return Component.translatable(
                "bewarethegreenone.config.position",
                Component.translatable(positionKey)
        );
    }


    // =========================
    // 爆発モード
    // =========================

    private Component getExplosionModeText() {

        return Component.translatable(
                "bewarethegreenone.config.explosion_mode",
                Component.translatable(
                        Config.explosionMode == 0
                                ? "bewarethegreenone.config.explosion_mode.vanilla"
                                : "bewarethegreenone.config.explosion_mode.enchanted"
                )
        );
    }


    // =========================
    // Render
    // =========================

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        this.renderBackground(guiGraphics);

        int rightColumnX =
                this.width / 2 + 35;

        int topY =
                this.height / 2 - 60;


        // タイトル
        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                30,
                0xFFFFFF
        );


        // 最大倍率ラベル
        Component maxMultiplierLabel =
                Component.translatable(
                        "bewarethegreenone.config.max_multiplier"
                );

        int labelWidth =
                this.font.width(
                        maxMultiplierLabel
                );

        int labelX =
                rightColumnX
                        + (80 - labelWidth) / 2;

        int labelY =
                topY
                        + (20 - this.font.lineHeight) / 2;

        guiGraphics.drawString(
                this.font,
                maxMultiplierLabel,
                labelX,
                labelY,
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

        saveMaxMultiplier();

        this.minecraft.setScreen(parent);
    }
}