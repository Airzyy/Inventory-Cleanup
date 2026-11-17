package dev.airz.inventorycleanup.mixin;

import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Shadow(remap = false) @Final private RecipeBookComponent recipeBookComponent;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void inventorycleanup$hidePlayerRecipeBook(CallbackInfo ci) {
        if (this.recipeBookComponent.isVisible()) this.recipeBookComponent.toggleVisibility();
    }

    @Inject(method = "renderBg", at = @At("TAIL"), remap = false)
    private void inventorycleanup$renderCuriosSlots(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object optionalObject = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) return;

            Object handler = optional.get();
            Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Map<?, ?> curios = (Map<?, ?>) handlerType.getMethod("getCurios").invoke(handler);
            Class<?> stacksHandlerType = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> dynamicStacksType = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            InventoryScreen screen = (InventoryScreen) (Object) this;
            int baseX = screen.getGuiLeft() + 180;
            int baseY = screen.getGuiTop() + 8;
            int index = 0;

            for (Map.Entry<?, ?> entry : curios.entrySet()) {
                Object stacksHandler = entry.getValue();
                boolean visible = (boolean) stacksHandlerType.getMethod("isVisible").invoke(stacksHandler);
                if (!visible) continue;
                Object stacks = stacksHandlerType.getMethod("getStacks").invoke(stacksHandler);
                int slots = (int) dynamicStacksType.getMethod("getSlots").invoke(stacks);
                for (int slot = 0; slot < slots; slot++) {
                    int x = baseX + (index % 3) * 18;
                    int y = baseY + (index / 3) * 18;
                    graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
                    graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
                    graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF373737);
                    ItemStack stack = (ItemStack) dynamicStacksType.getMethod("getStackInSlot", int.class).invoke(stacks, slot);
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, x + 1, y + 1);
                        graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
                    }
                    index++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Curios is required by the mod metadata; reflection keeps its API out of the compile classpath.
        }
    }
}
