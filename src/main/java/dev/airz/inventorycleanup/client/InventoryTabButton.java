package dev.airz.inventorycleanup.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.common.network.client.CPacketOpenVanilla;

public final class InventoryTabButton extends Button {
    private static final ResourceLocation SKILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reskillable", "textures/gui/skills.png");
    private static final String CURIOS_SCREEN =
            "top.theillusivec4.curios.client.gui.CuriosScreen";

    private final Screen owner;
    private final boolean inventoryTab;

    public InventoryTabButton(Screen owner, int x, int y, boolean inventoryTab) {
        super(x, y, 28, 31, Component.empty(),
                button -> activate(owner, inventoryTab), DEFAULT_NARRATION);
        this.owner = owner;
        this.inventoryTab = inventoryTab;
        this.setTooltip(Tooltip.create(Component.literal(inventoryTab ? "Inventory" : "Skills")));
    }

    private static void activate(Screen owner, boolean inventoryTab) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        boolean inCurios = owner.getClass().getName().equals(CURIOS_SCREEN);
        boolean inSkills = owner.getClass().getName().equals("net.bandit.reskillable.client.screen.SkillScreen");
        boolean inInventory = owner instanceof InventoryScreen;

        if (inventoryTab) {
            if (inInventory) return;
            if (inCurios || inSkills) switchBackToVanillaMenu(player);
            minecraft.setScreen(new InventoryScreen(player));
        } else {
            if (inSkills) return;
            if (inCurios) switchBackToVanillaMenu(player);
            try {
                Screen skills = (Screen) Class.forName("net.bandit.reskillable.client.screen.SkillScreen")
                        .getConstructor().newInstance();
                minecraft.setScreen(skills);
            } catch (ReflectiveOperationException ignored) {
                // Reskillable is optional.
            }
        }
    }

    private static void switchBackToVanillaMenu(Player player) {
        ItemStack carried = player.containerMenu.getCarried().copy();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.inventoryMenu.setCarried(carried);
            PacketDistributor.sendToServer(new CPacketOpenVanilla(carried));
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = inventoryTab
                ? owner instanceof InventoryScreen || !owner.getClass().getName().equals(
                        "net.bandit.reskillable.client.screen.SkillScreen")
                : owner.getClass().getName().equals("net.bandit.reskillable.client.screen.SkillScreen");

        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Rotate the side-tab artwork into the top edge, then mirror it horizontally.
        pose.translate(getX() + 28, getY() + 31, 0);
        pose.scale(-1.0F, 1.0F, 1.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        // Mirroring reverses the quad winding; disable culling only for this tab texture.
        RenderSystem.disableCull();
        graphics.blit(SKILL_TEXTURE, 0, 0, selected ? 31 : 0, 166, 31, 28);
        RenderSystem.enableCull();
        pose.popPose();

        if (inventoryTab) {
            graphics.renderItem(Items.CHEST.getDefaultInstance(), getX() + 6, getY() + 7);
        } else {
            graphics.blit(SKILL_TEXTURE, getX() + 6, getY() + 7, 240, 144, 16, 16);
        }
    }
}
