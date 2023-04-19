package nesemu;

public class Mapper003Cartridge extends Cartridge {
    private static final int BANK_SIZE = 8192;

    private int bankSelect;

    public Mapper003Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasPrgRAM, boolean hasChrRAM) {
        super(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
    }

    @Override
    byte readPrgROMByte(short address) {
        return prgROM[Short.toUnsignedInt(address) % prgROM.length];
    }

    @Override
    void writePrgROMByte(short address, byte value) {
        if (Short.toUnsignedInt(address) >= 0x8000)
            bankSelect = Byte.toUnsignedInt(value);
    }

    @Override
    public byte ppuReadByte(short address) {
        return chrROM[(bankSelect * BANK_SIZE +
                Short.toUnsignedInt(address) % BANK_SIZE) % chrROM.length];
    }
}
