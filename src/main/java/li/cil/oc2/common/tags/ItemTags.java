package li.cil.oc2.common.tags;

import li.cil.oc2.api.API;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.Tags;

import static net.minecraft.tags.ItemTags.createOptional;

public final class ItemTags {
    public static final Tags.IOptionalNamedTag<Item> DEVICES = tag("devices");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_MEMORY = tag("devices/memory");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_HARD_DRIVE = tag("devices/hard_drive");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_FLASH_MEMORY = tag("devices/flash_memory");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_CARD = tag("devices/card");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_ROBOT_MODULE = tag("devices/robot_module");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_FLOPPY = tag("devices/floppy");
    public static final Tags.IOptionalNamedTag<Item> DEVICES_NETWORK_TUNNEL = tag("devices/network_tunnel");

    public static final Tags.IOptionalNamedTag<Item> CABLES = tag("cables");
    public static final Tags.IOptionalNamedTag<Item> WRENCHES = tag("wrenches");
    public static final Tags.IOptionalNamedTag<Item> DEVICE_NEEDS_REBOOT = tag("device_needs_reboot");

    ///////////////////////////////////////////////////////////////////

    public static void initialize() {
    }

    ///////////////////////////////////////////////////////////////////

    private static Tags.IOptionalNamedTag<Item> tag(final String name) {
        return createOptional(new ResourceLocation(API.MOD_ID, name));
    }
}
