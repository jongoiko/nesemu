package nesemu;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/* The NES is made up of many different components (CPU, PPU, game cartridge,
 * controllers, system memory, etc.). The interconnection between such components
 * is handled by this class, such that a device connected to an address space may
 * be written to or read from by any other device in the address space.
 * See https://www.nesdev.org/wiki/CPU_memory_map
 *
 * Note that some memory locations and ports may be accessed from more than one
 * memory address, depending on the way the 16-bit addresses are decoded within
 * each device; this is known as "mirroring":
 * https://www.nesdev.org/wiki/Mirroring#Memory_Mirroring
 */

public class AddressSpace implements Serializable {
    private final List<MemoryMapped> devices;

    public AddressSpace() {
        devices = new ArrayList<>();
    }

    public void addDevice(MemoryMapped device) {
        device.linkAddressSpace(this);
        devices.add(device);
    }

    public void removeDevice(MemoryMapped device) {
        devices.remove(device);
    }

    public byte readByte(short address) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address))
                return device.readByteFromDevice(address);
        return 0;
    }

    public void writeByte(short address, byte value) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address)) {
                device.writeByteToDevice(address, value);
                return;
            }
    }
}
