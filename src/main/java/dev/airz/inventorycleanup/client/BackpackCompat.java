package dev.airz.inventorycleanup.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Optional Sophisticated Backpacks integration.
 *
 * <p>Everything goes through reflection so this mod neither needs the backpack jars to compile nor
 * to run. If Sophisticated Backpacks is missing (or changes these internals) every method here
 * quietly degrades to "no backpacks".
 *
 * <p>Backpacks are found with Sophisticated Backpacks' own {@code PlayerInventoryProvider}, so the
 * list covers exactly what the "open backpack" keybind can see (Curios slots, chest slot, offhand,
 * main inventory) and comes back in the same order the keybind searches. The first entry is
 * therefore the backpack the keybind would open.
 */
public final class BackpackCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "sophisticatedbackpacks";
    private static final String PROVIDER = "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider";
    private static final String CONSUMER = PROVIDER + "$BackpackInventorySlotConsumer";
    private static final String HANDLER = "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryHandler";
    private static final String BACKPACK_ITEM = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem";
    private static final String OPEN_PAYLOAD = "net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenPayload";
    private static final String BACKPACK_SCREEN = "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen";
    private static final String ITEM_CONTEXT = "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext$Item";

    // Same handler names / slot numbers Sophisticated Backpacks' own keybind sends.
    private static final String ARMOR = "armor";
    private static final String OFFHAND = "offhand";
    private static final int CHEST_SLOT = 38;
    private static final int OFFHAND_SLOT = 40;

    private static Api api;
    private static boolean apiFailed;

    private BackpackCompat() {}

    /**
     * Where a backpack lives. {@code slot} is the slot inside its inventory handler, exactly as
     * the provider reports it.
     */
    public record BackpackRef(String handler, String identifier, int slot) {
        /** The slot number Sophisticated Backpacks expects in its open packet for this location. */
        int payloadSlot() {
            if (ARMOR.equals(handler)) return CHEST_SLOT;
            if (OFFHAND.equals(handler)) return OFFHAND_SLOT;
            return slot;
        }
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isBackpackScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals(BACKPACK_SCREEN);
    }

    /** All backpacks the player carries, in the order the open-backpack keybind would try them. */
    public static List<BackpackRef> findBackpacks(Player player) {
        Api api = api();
        if (api == null || player == null) return List.of();

        List<BackpackRef> found = new ArrayList<>();
        try {
            Object consumer = Proxy.newProxyInstance(
                    api.consumerType.getClassLoader(),
                    new Class<?>[] {api.consumerType},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "accept" -> {
                                if (args != null && args.length == 4) {
                                    found.add(new BackpackRef((String) args[1], (String) args[2], (Integer) args[3]));
                                }
                                // false = keep going, so every backpack is reported.
                                return Boolean.FALSE;
                            }
                            case "hashCode" -> {
                                return System.identityHashCode(proxy);
                            }
                            case "equals" -> {
                                return args != null && args.length == 1 && proxy == args[0];
                            }
                            case "toString" -> {
                                return "InventoryCleanup backpack collector";
                            }
                            default -> {
                                return null;
                            }
                        }
                    });
            api.runOnBackpacks.invoke(api.providerGet.invoke(null), player, consumer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("listing backpacks", e);
            return List.of();
        }
        return found;
    }

    /** The live stack at this location, or empty if it is no longer a backpack. */
    public static ItemStack getStack(Player player, BackpackRef ref) {
        Api api = api();
        if (api == null || player == null || ref == null) return ItemStack.EMPTY;
        try {
            Object provider = api.providerGet.invoke(null);
            Optional<?> handler = (Optional<?>) api.getHandler.invoke(provider, ref.handler());
            if (handler.isEmpty()) return ItemStack.EMPTY;
            Object stack = api.handlerGetStack.invoke(handler.get(), player, ref.identifier(), ref.slot());
            if (stack instanceof ItemStack itemStack && api.backpackItem.isInstance(itemStack.getItem())) {
                return itemStack;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The slot can vanish between frames (e.g. a Curios slot count change). Treat as empty.
        }
        return ItemStack.EMPTY;
    }

    /** Asks the server to open that specific backpack, like the keybind does for a hovered one. */
    public static void open(BackpackRef ref) {
        Api api = api();
        if (api == null || ref == null) return;
        try {
            Object payload = api.openPayload.newInstance(ref.payloadSlot(), ref.identifier(), ref.handler());
            PacketDistributor.sendToServer((CustomPacketPayload) payload);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("opening a backpack", e);
        }
    }

    /** Whether this menu is the (top-level) backpack found at {@code ref}. */
    public static boolean isOpen(AbstractContainerMenu menu, BackpackRef ref) {
        if (menu == null || ref == null) return false;
        try {
            Method getContext = menu.getClass().getMethod("getBackpackContext");
            Object context = getContext.invoke(menu);
            // Exact class on purpose: nested backpacks use a subclass and must not light up their parent.
            if (context == null || !context.getClass().getName().equals(ITEM_CONTEXT)) return false;

            String handler = (String) read(context, "handlerName");
            String identifier = (String) read(context, "identifier");
            int slot = (Integer) read(context, "backpackSlotIndex");

            if (!ref.handler().equals(handler) || !ref.identifier().equals(identifier)) return false;
            // The chest and offhand handlers only have one slot, and the server stores a remapped
            // number for them, so only compare slots where the number actually means something.
            return ARMOR.equals(handler) || OFFHAND.equals(handler) || ref.slot() == slot;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Object read(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Api api() {
        if (api == null && !apiFailed) {
            if (!isLoaded()) {
                apiFailed = true;
                return null;
            }
            try {
                api = new Api();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                fail("initialising", e);
            }
        }
        return api;
    }

    private static void fail(String what, Throwable error) {
        if (!apiFailed) {
            LOGGER.warn("Sophisticated Backpacks integration disabled: problem while {}", what, error);
        }
        apiFailed = true;
        api = null;
    }

    /** Resolved reflection handles, built once. */
    private static final class Api {
        final Class<?> consumerType;
        final Class<?> backpackItem;
        final Method providerGet;
        final Method runOnBackpacks;
        final Method getHandler;
        final Method handlerGetStack;
        final Constructor<?> openPayload;

        Api() throws ReflectiveOperationException {
            Class<?> provider = Class.forName(PROVIDER);
            Class<?> handler = Class.forName(HANDLER);
            consumerType = Class.forName(CONSUMER);
            backpackItem = Class.forName(BACKPACK_ITEM);
            providerGet = provider.getMethod("get");
            runOnBackpacks = provider.getMethod("runOnBackpacks", Player.class, consumerType);
            getHandler = provider.getMethod("getPlayerInventoryHandler", String.class);
            handlerGetStack = handler.getMethod("getStackInSlot", Player.class, String.class, int.class);
            openPayload = Class.forName(OPEN_PAYLOAD).getConstructor(int.class, String.class, String.class);
        }
    }
}
