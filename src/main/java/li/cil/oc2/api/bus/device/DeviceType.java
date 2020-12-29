package li.cil.oc2.api.bus.device;

import li.cil.oc2.api.API;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Implementations describe individual slot types. Slot types are only used
 * for item devices, and mimic the connection type of devices in the real world,
 * such as PCI vs SATA, in a simplified manner.
 * <p>
 * For built-in slot types, see {@link DeviceTypes}.
 */
public interface DeviceType extends IForgeRegistryEntry<DeviceType> {
    /**
     * The registry name of the registry holding device types.
     */
    ResourceLocation REGISTRY = new ResourceLocation(API.MOD_ID, "device_type");

    /**
     * An icon rendered as background of empty slots, visually indicating the
     * type of the slot.
     *
     * @return the background icon for this device type.
     */
    ResourceLocation getBackgroundIcon();

    /**
     * The display name of this device type, may be shown as tooltip for slots
     * of this type.
     *
     * @return the display name for this device type.
     */
    ITextComponent getName();
}