package nesemu;

public class Mapper001Cartridge extends Cartridge {
    private static final int PRG_BANK_SIZE = 16384;
    private static final int CHR_BANK_SIZE = 8192;

    private byte shiftRegister;
    private PrgBankMode prgBankMode;
    private ChrBankMode chrBankMode;

    private int prgBankSelect;
    private int chrLowerBankSelect;
    private int chrUpperBankSelect;

    private enum PrgBankMode {
        SWITCH_32KB,
        FIX_16KB_FIRST_HALF,
        FIX_16KB_SECOND_HALF
    }

    private enum ChrBankMode {
        SWITCH_8KB,
        SWITCH_TWO_4KB
    }

    public Mapper001Cartridge(byte[] prgROM, byte[] chrROM, Mirroring mirroring,
            boolean hasPrgRAM, boolean hasChrRAM) {
        super(prgROM, chrROM, mirroring, hasPrgRAM, hasChrRAM);
        shiftRegister = (byte)0x10;
        prgBankMode = PrgBankMode.FIX_16KB_SECOND_HALF;
    }

    @Override
    public byte readMappedPrgByte(short address) {
        int mappedAddress = Short.toUnsignedInt(address), baseAddress = 0;
        switch (prgBankMode) {
            case FIX_16KB_FIRST_HALF ->
                baseAddress = mappedAddress >= 0xC000 ? prgBankSelect * PRG_BANK_SIZE : 0;
            case FIX_16KB_SECOND_HALF ->
                baseAddress = mappedAddress >= 0xC000 ? prgROM.length - PRG_BANK_SIZE :
                        prgBankSelect * PRG_BANK_SIZE;
            case SWITCH_32KB ->
                baseAddress = (prgBankSelect & ~1) * 2 * PRG_BANK_SIZE;
        }
        return prgROM[(baseAddress + (mappedAddress % PRG_BANK_SIZE)) % prgROM.length];
    }

    @Override
    void writeMappedPrgByte(short address, byte value) {
        int intAddress = Short.toUnsignedInt(address);
        if (intAddress >= 0x8000) {
            if ((value & 0x80) != 0)
                reset();
            else {
                boolean isFifthWrite = (shiftRegister & 1) != 0;
                shiftRegister >>>= 1;
                shiftRegister |= 0x10 * (value & 1);
                if (isFifthWrite) {
                    updateBanks((address & 0x6000) >>> 13);
                    reset();
                }
            }
        } else if (intAddress >= 0x6000 && intAddress < 0x8000 && hasPrgRAM)
            prgRAM[Short.toUnsignedInt(address) % prgRAM.length] = value;
    }

    @Override
    public byte ppuReadByte(short address) {
        int mappedAddress = Short.toUnsignedInt(address), baseAddress = 0;
        if (mappedAddress < 0x1000)
            baseAddress = chrBankMode == ChrBankMode.SWITCH_8KB ?
                    (chrLowerBankSelect & ~1) * CHR_BANK_SIZE :
                    chrLowerBankSelect * (CHR_BANK_SIZE / 2);
        else if (chrBankMode == ChrBankMode.SWITCH_TWO_4KB) {
            baseAddress = chrUpperBankSelect * (CHR_BANK_SIZE / 2);
            mappedAddress &= 0xFFF;
        }
        return chrROM[(baseAddress + mappedAddress) % chrROM.length];
    }

    private void reset() {
        shiftRegister = (byte)0x10;
        prgBankMode = PrgBankMode.FIX_16KB_SECOND_HALF;
    }

    private void updateBanks(int registerSelectBits) {
        switch (registerSelectBits) {
            case 0 ->  updateBankingModes();
            case 1 ->  chrLowerBankSelect = shiftRegister;
            case 2 ->  chrUpperBankSelect = shiftRegister;
            default -> prgBankSelect = shiftRegister & 0xF; // TODO: upper bit
        }
    }

    private void updateBankingModes() {
        switch (shiftRegister & 3) {
            case 0 -> mirroring = Mirroring.SINGLE_SCREEN_LOWER;
            case 1 -> mirroring = Mirroring.SINGLE_SCREEN_UPPER;
            case 2 -> mirroring = Mirroring.VERTICAL;
            case 3 -> mirroring = Mirroring.HORIZONTAL;
        }
        switch ((shiftRegister & 0xC) >>> 2) {
            case 2 ->  prgBankMode = PrgBankMode.FIX_16KB_FIRST_HALF;
            case 3 ->  prgBankMode = PrgBankMode.FIX_16KB_SECOND_HALF;
            default -> prgBankMode = PrgBankMode.SWITCH_32KB;
        }
        chrBankMode = (shiftRegister & 0x10) != 0 ?
                ChrBankMode.SWITCH_TWO_4KB : ChrBankMode.SWITCH_8KB;
    }
}
