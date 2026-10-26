package dev.mathproof.client;

import dev.mathproof.MathProofMod;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MathProofMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private static KeyMapping openKey;
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        openKey = new KeyMapping("key.mathproof.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.mathproof");
        event.register(openKey);
    }

    @EventBusSubscriber(modid = MathProofMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (openKey != null && openKey.consumeClick()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null && !(minecraft.screen instanceof ProofBoardScreen)) {
                    minecraft.setScreen(new ProofBoardScreen());
                }
            }
        }
    }
}
