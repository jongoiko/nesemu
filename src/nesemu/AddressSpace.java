package nesemu;

import java.util.*;

public class AddressSpace {
    private final List<MemoryMapped> devices;

    public AddressSpace() {
        devices = new ArrayList<>();
    }

    public void addDevice(MemoryMapped device) {
        device.linkAddressSpace(this);
        devices.add(device);
    }

    public byte readByte(short address) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address))
                return device.readByte(address);
        return 0;
    }

    public void writeByte(short address, byte value) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address)) {
                device.writeByte(address, value);
                return;
            }
    }
}
