package nesemu;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class Cartridge extends MemoryMapped {
    private final static int PRG_ROM_BLOCK_SIZE = 16384;
    private final static int CHR_ROM_BLOCK_SIZE = 8192;

    private final byte prgROM[];
    private final byte chrROM[];
    public final Mirroring mirroring;
    private boolean hasChrRAM;

    public enum Mirroring {
        HORIZONTAL,
        VERTICAL,
        FOUR_NAMETABLES
    }

    public Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasChrRAM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
        this.mirroring = mirroring;
        this.hasChrRAM = hasChrRAM;
    }

    @Override
    boolean addressIsMapped(short address) {
        return Short.toUnsignedInt(address) >= 0x4020;
    }

    @Override
    byte readByteFromDevice(short address) {
        return prgROM[Short.toUnsignedInt(address) % prgROM.length];
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public byte ppuReadByte(short address) {
        return chrROM[Short.toUnsignedInt(address) % chrROM.length];
    }

    public void ppuWriteByte(short address, byte value) {
        if (hasChrRAM)
            chrROM[Short.toUnsignedInt(address) % chrROM.length] = value;
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
        byte prgROM[] = stream.readNBytes(PRG_ROM_BLOCK_SIZE * prgROMSize);
        byte chrROM[] = hasChrRAM ? new byte[CHR_ROM_BLOCK_SIZE] :
                stream.readNBytes(CHR_ROM_BLOCK_SIZE * chrROMSize);
        return new Cartridge(prgROM, chrROM, mirroring, hasChrRAM);
    }
}
