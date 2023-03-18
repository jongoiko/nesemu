package nesemu;

public abstract class MemoryMapped {
    AddressSpace addressSpace;

    abstract boolean addressIsMapped(short address);
    abstract byte readByteFromDevice(short address);
    abstract void writeByteToDevice(short address, byte value);

    void linkAddressSpace(AddressSpace addressSpace) {
        this.addressSpace = addressSpace;
    }
}
