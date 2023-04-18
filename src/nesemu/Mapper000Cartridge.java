package nesemu;

public class Mapper000Cartridge extends Cartridge {
    public Mapper000Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasPrgRAM, boolean hasChrRAM) {
        super(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
    }

    @Override
    byte readMappedPrgByte(short address) {
        return prgROM[Short.toUnsignedInt(address) % prgROM.length];
    }

    @Override
    void writeMappedPrgByte(short address, byte value) {
        prgROM[Short.toUnsignedInt(address) % prgROM.length] = value;
    }
}
