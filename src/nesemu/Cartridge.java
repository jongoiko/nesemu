package nesemu;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class Cartridge extends MemoryMapped {
    private final static int PRG_ROM_BLOCK_SIZE = 16384;
    private final static int CHR_ROM_BLOCK_SIZE = 8192;

    private final byte prgROM[];
    private final byte chrROM[];

    public Cartridge(byte[] prgROM, byte[] chrROM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
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

    static public Cartridge fromINESFile(String filePath) throws IOException {
        DataInputStream stream = new DataInputStream(new FileInputStream(filePath));
        final byte iNESHeader[] = { 0x4E, 0x45, 0x53, 0x1A };
        for (byte headerByte : iNESHeader)
            if (stream.readByte() != headerByte)
                throw new IOException("Invalid iNES header");
        final byte prgROMSize = stream.readByte();
        final byte chrROMSize = stream.readByte();
        final int mapperNumber = ((stream.readByte() & 0xFF00) >>> 4) |
                (stream.readByte() & 0xFF00);
        stream.readNBytes(8); // TODO
        // Assume no trainer
        byte prgROM[] = stream.readNBytes(PRG_ROM_BLOCK_SIZE * prgROMSize);
        byte chrROM[] = stream.readNBytes(CHR_ROM_BLOCK_SIZE * chrROMSize);
        return new Cartridge(prgROM, chrROM);
    }
}
