package dev.airz.inventorycleanup.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;

@EventBusSubscriber(modid = "inventorycleanup", value = Dist.CLIENT)
public final class InventoryScreenHandler {
    private InventoryScreenHandler() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof InventoryScreen && !(screen instanceof CreativeModeInventoryScreen)) {
            // Remove the Curios launcher and Apothic Attributes panel/button from this screen only.
            for (GuiEventListener listener : List.copyOf(screen.children())) {
                String name = listener.getClass().getName();
                if (name.equals("top.theillusivec4.curios.client.gui.CuriosButton")
                        || name.startsWith("dev.shadowsoffire.apothic_attributes.client.AttributesGui$")) {
                    screen.removeWidget(listener);
                }
            }

            // Reskillable's tab uses GUI-relative coordinates. Put it beside the new inventory tab.
            setReskillableTabPosition(31, -28);
            addInventoryTab(screen, true);
        } else if (screen.getClass().getName().equals("net.bandit.reskillable.client.screen.SkillScreen")) {
            setReskillableTabPosition(31, -28);
            addInventoryTab(screen, false);
        }
    }

    private static void setReskillableTabPosition(int x, int y) {
        try {
            Class<?> tabs = Class.forName("net.bandit.reskillable.client.screen.InventoryTabs");
            tabs.getMethod("setPosition", int.class, int.class).invoke(null, x, y);
        } catch (ReflectiveOperationException ignored) {
            // Reskillable is an optional integration at runtime.
        }
    }

    private static void addInventoryTab(Screen screen, boolean alreadyInventory) {
        int left = screen instanceof InventoryScreen inventory ? inventory.getGuiLeft() : (screen.width - 176) / 2;
        int top = screen instanceof InventoryScreen inventory ? inventory.getGuiTop() : (screen.height - 166) / 2;
        Button tab = Button.builder(Component.translatable("inventorycleanup.tab.inventory"), button -> {
            if (!alreadyInventory && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            }
        }).bounds(left, top - 28, 31, 28).tooltip(
                net.minecraft.client.gui.components.Tooltip.create(Component.translatable("inventorycleanup.tab.inventory")))
                .build();
        screen.addRenderableWidget(tab);
        Renderable icon = (graphics, mouseX, mouseY, partialTick) ->
                graphics.renderItem(Items.CHEST.getDefaultInstance(), left + 8, top - 22);
        screen.addRenderableOnly(icon);
    }
}
