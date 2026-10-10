package com.osucraft.client;

import com.osucraft.OsuCraft;
import com.osucraft.core.Library;
import com.osucraft.gui.OsuMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class OsuCraftClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Library.root = FabricLoader.getInstance().getGameDir().resolve("osucraft");
        Library.ensureDirs();
        OsuCraft.openMenuHook = () -> MinecraftClient.getInstance().setScreen(new OsuMenuScreen(null));

        // Atajo extra (se puede cambiar o quitar en Controles): tecla O abre el menú sin necesidad del bloque.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(OsuCraft.MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.osucraft.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new OsuMenuScreen(null));
                }
            }
        });
    }
}
