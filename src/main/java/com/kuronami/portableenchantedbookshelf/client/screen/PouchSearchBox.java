package com.kuronami.portableenchantedbookshelf.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * PEB の検索バー — vanilla {@link EditBox} を継承し、 **focused 時 vanilla の inventory key
 * (E) を奪う**ことで Screen が onClose() を実行するのを防ぐ。
 *
 * <p>3 mod 共通 pattern (Sophisticated {@code TextBox.java:54-63}, Tom's
 * {@code AbstractStorageTerminalScreen.java:651-668}, AE2 {@code AETextField.java:106-115})
 * から確定した正攻法:
 * <ul>
 *   <li>{@code focused == false} → {@code return false} (super 任せ)</li>
 *   <li>{@code focused == true} → {@code super.keyPressed} で処理した後、
 *       <b>ESC / TAB 以外は {@code return true}</b> で consume (vanilla AbstractContainerScreen の
 *       {@code keyInventory.matches(...)} 判定まで到達させない)</li>
 * </ul>
 *
 * <p>v2 で僕が vanilla {@code EditBox} をそのまま {@code addRenderableWidget} した結果、
 * 文字入力 (E など) を {@code EditBox.keyPressed} が {@code false} で返し、 vanilla の
 * AbstractContainerScreen が Inventory key として処理して Screen が閉じた bug の対策。
 */
public class PouchSearchBox extends EditBox {

    public PouchSearchBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        super.keyPressed(keyCode, scanCode, modifiers);
        // ESC / TAB はそのまま super に処理させる (close / focus 移動)、 それ以外は consume
        return keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB;
    }
}
