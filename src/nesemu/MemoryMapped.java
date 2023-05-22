package nesemu;

import java.io.Serializable;

public abstract class MemoryMapped implements Serializable {
    AddressSpace addressSpace;

    public abstract boolean addressIsMapped(short address);
    public abstract byte readByteFromDevice(short address);
    public abstract void writeByteToDevice(short address, byte value);

    void linkAddressSpace(AddressSpace addressSpace) {
        this.addressSpace = addressSpace;
    }
}
