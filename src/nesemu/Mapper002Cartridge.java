package nesemu;

public class Mapper002Cartridge extends Cartridge {
    private static final int BANK_SIZE = 16384;

    private int bankSelect;

    public Mapper002Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasPrgRAM, boolean hasChrRAM) {
        super(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
    }

    @Override
    byte readMappedPrgByte(short address) {
        int mappedAddress = Short.toUnsignedInt(address);
        mappedAddress = (mappedAddress >= 0xC000 ? prgROM.length - BANK_SIZE :
                bankSelect * BANK_SIZE) + mappedAddress % BANK_SIZE;
        return prgROM[mappedAddress % prgROM.length];
    }

    @Override
    void writeMappedPrgByte(short address, byte value) {
        if (Short.toUnsignedInt(address) >= 0x8000)
            bankSelect = value & 0xFF;
    }
}
