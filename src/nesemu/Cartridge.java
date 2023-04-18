package nesemu;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class Cartridge extends MemoryMapped {
    private final static int PRG_ROM_BLOCK_SIZE = 16384;
    private final static int CHR_ROM_BLOCK_SIZE = 8192;

    final byte prgROM[];
    final byte chrROM[];
    final byte prgRAM[];

    public Mirroring mirroring;

    boolean hasPrgRAM;
    boolean hasChrRAM;

    public enum Mirroring {
        SINGLE_SCREEN_LOWER,
        SINGLE_SCREEN_UPPER,
        HORIZONTAL,
        VERTICAL,
        FOUR_NAMETABLES
    }

    public Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasPrgRAM, boolean hasChrRAM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
        this.prgRAM = new byte[0x2000];
        this.mirroring = mirroring;
        this.hasPrgRAM = hasPrgRAM;
        this.hasChrRAM = hasChrRAM;
    }

    abstract byte readMappedPrgByte(short address);
    abstract void writeMappedPrgByte(short address, byte value);

    @Override
    boolean addressIsMapped(short address) {
        return Short.toUnsignedInt(address) >= 0x4020;
    }

    @Override
    byte readByteFromDevice(short address) {
        int intAddress = Short.toUnsignedInt(address);
        if (hasPrgRAM && intAddress >= 0x6000 && intAddress < 0x8000)
            return prgRAM[intAddress % prgRAM.length];
        return readMappedPrgByte(address);
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        int intAddress = Short.toUnsignedInt(address);
        if (hasPrgRAM && intAddress >= 0x6000 && intAddress < 0x8000)
            prgRAM[intAddress % prgRAM.length] = value;
        else
            writeMappedPrgByte(address, value);
    }

    public byte ppuReadByte(short address) {
        return chrROM[Short.toUnsignedInt(address) % chrROM.length];
    }

    public void ppuWriteByte(short address, byte value) {
        if (hasChrRAM)
            chrROM[Short.toUnsignedInt(address) % chrROM.length] = value;
    }

    public void reset() {

    }

    public static Cartridge fromINESFile(String filePath) throws IOException {
        DataInputStream stream = new DataInputStream(new FileInputStream(filePath));
        final byte iNESHeader[] = { 0x4E, 0x45, 0x53, 0x1A };
        for (byte headerByte : iNESHeader)
            if (stream.readByte() != headerByte)
                throw new IOException("Invalid iNES header");
        final byte prgROMSize = stream.readByte();
        final byte chrROMSize = stream.readByte();
        final byte flags6 = stream.readByte();
        final byte flags7 = stream.readByte();
        final int mapperNumber = ((flags6 & 0xF0) >>> 4) | (flags7 & 0xF0);
        Mirroring mirroring;
        if ((flags6 & 8) != 0)
            mirroring = Mirroring.FOUR_NAMETABLES;
        else if ((flags6 & 1) != 0)
            mirroring = Mirroring.VERTICAL;
        else
            mirroring = Mirroring.HORIZONTAL;
        stream.readNBytes(8); // TODO
        // Assume no trainer
        boolean hasChrRAM = chrROMSize == 0;
        boolean hasPrgRAM = (flags6 & 2) != 0;
        byte prgROM[] = stream.readNBytes(PRG_ROM_BLOCK_SIZE * prgROMSize);
        byte chrROM[] = hasChrRAM ? new byte[CHR_ROM_BLOCK_SIZE] :
                stream.readNBytes(CHR_ROM_BLOCK_SIZE * chrROMSize);
        return assignMapper(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM, mapperNumber);
    }

    private static Cartridge assignMapper(byte prgROM[], byte chrROM[],
            Mirroring mirroring, boolean hasPrgRAM, boolean hasChrRAM, int mapperNumber)
            throws UnsupportedOperationException {
        switch (mapperNumber) {
            case 0 -> {
                return new Mapper000Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
            case 1 -> {
                return new Mapper001Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
        }
        throw new UnsupportedOperationException("Unsupported mapper (number " +
                mapperNumber + ")");
    }
}
