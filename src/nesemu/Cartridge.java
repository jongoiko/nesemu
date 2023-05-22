package nesemu;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

/* NES games were distributed in cartridges. They usually had separate memories
 * for the games' code (PRG-ROM, mapped to the CPU's address space) and graphics
 * (CHR-ROM, mapped to the PPU's address space).
 *
 * Without any address translation, code and graphics data could be up to 32KB
 * and 8KB respectively. To overcome this limitation, manufacturers often included
 * special chips inside the cartridges (called "mappers") which translated the
 * addresses emitted by the processors to index the actual memory chips,
 * effectively extending the system's available memory.
 *
 * For a more in-depth explanation (as well as a listing of known mappers) see
 * https://www.nesdev.org/wiki/Mapper.
 */

public abstract class Cartridge extends MemoryMapped {
    private final static int PRG_ROM_BLOCK_SIZE = 16384;
    private final static int CHR_ROM_BLOCK_SIZE = 8192;

    public final byte prgROM[];
    public final byte chrROM[];
    public byte prgRAM[];

    public Mirroring mirroring;

    public boolean hasPrgRAM;
    public boolean hasChrRAM;

    private String name = "";

    /* Although the PPU's address space can fit 4 nametables, usually only two
     * could be stored in memory. Thus, a mirroring scheme was necessary, such
     * that the PPU would "see" the same nametables in different address ranges.
     * This was hardwired into the cartridge in earlier games, and some mappers
     * supported switching the mirroring mode at runtime. See
     * https://www.nesdev.org/wiki/Mirroring#Nametable_Mirroring
     */
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

    // To be implemented by Cartridge subclasses corresponding to a specific
    // mapper.
    abstract byte readPrgROMByte(short address);
    abstract void writePrgROMByte(short address, byte value);

    @Override
    public boolean addressIsMapped(short address) {
        return Short.toUnsignedInt(address) >= 0x4020;
    }

    @Override
    public byte readByteFromDevice(short address) {
        int intAddress = Short.toUnsignedInt(address);
        if (hasPrgRAM && intAddress >= 0x6000 && intAddress < 0x8000)
            return readPrgRAMByte(address);
        return readPrgROMByte(address);
    }

    @Override
    public void writeByteToDevice(short address, byte value) {
        int intAddress = Short.toUnsignedInt(address);
        if (hasPrgRAM && intAddress >= 0x6000 && intAddress < 0x8000)
            writePrgRAMByte(address, value);
        else
            writePrgROMByte(address, value);
    }

    public byte readPrgRAMByte(short address) {
        return prgRAM[Short.toUnsignedInt(address) % prgRAM.length];
    }

    public void writePrgRAMByte(short address, byte value) {
        prgRAM[Short.toUnsignedInt(address) % prgRAM.length] = value;
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

    public String getName() {
        return name;
    }

    /* The "de facto" file format for NES games is the .nes or iNES format
     * (https://www.nesdev.org/wiki/INES). Although the format has many special
     * fields to support as many games as possible, this method only uses the
     * most basic fields.
     */
    public static Cartridge fromINESFile(String filePath) throws IOException,
            UnsupportedMapperException, IllegalArgumentException {
        DataInputStream stream = new DataInputStream(new FileInputStream(filePath));
        final byte iNESHeader[] = { 0x4E, 0x45, 0x53, 0x1A };
        for (byte headerByte : iNESHeader)
            if (stream.readByte() != headerByte)
                throw new IllegalArgumentException("Invalid iNES header");
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
        stream.readNBytes(8);
        // Assume no trainer
        boolean hasChrRAM = chrROMSize == 0;
        boolean hasPrgRAM = (flags6 & 2) != 0;
        byte prgROM[] = stream.readNBytes(PRG_ROM_BLOCK_SIZE * prgROMSize);
        byte chrROM[] = hasChrRAM ? new byte[CHR_ROM_BLOCK_SIZE] :
                stream.readNBytes(CHR_ROM_BLOCK_SIZE * chrROMSize);
        Cartridge cartridge =
                assignMapper(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM, mapperNumber);
        cartridge.name = filePath.substring(filePath.lastIndexOf("/") + 1,
                filePath.lastIndexOf('.'));
        return cartridge;
    }

    private static Cartridge assignMapper(byte prgROM[], byte chrROM[],
            Mirroring mirroring, boolean hasPrgRAM, boolean hasChrRAM, int mapperNumber)
            throws UnsupportedMapperException {
        switch (mapperNumber) {
            case 0 -> {
                return new Mapper000Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
            case 1 -> {
                return new Mapper001Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
            case 2 -> {
                return new Mapper002Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
            case 3 -> {
                return new Mapper003Cartridge(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
            }
        }
        throw new UnsupportedMapperException(mapperNumber);
    }
}
