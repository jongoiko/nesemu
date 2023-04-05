package nesemu;

public class CPU extends MemoryMapped {
    private byte regA;
    private byte regY;
    private byte regX;
    private byte regS;
    private byte regP;
    private short regPC;

    private byte regOAMDMA;
    private int dmaCyclesLeft;
    private byte dmaBuffer;

    private short cyclesUntilNextInstruction;
    private short operandEffectiveAddress;
    private boolean isMemoryOperand;
    private boolean branchDestinationPageCrossed;
    private int cycleCount;

    public boolean requestNMI;

    public CPU() {
        regP = (byte)0x24;
        regS = (byte)0xFD;
        requestNMI = false;
    }

    private void log(Instruction instruction, short pc) {
        System.out.print(String.format("%04X  ", pc));
        System.out.print(String.format("%02X ", addressSpace.readByte(pc)) +
                 String.format("%02X ", addressSpace.readByte((short)(pc + 1))) +
                 String.format("%02X  ", addressSpace.readByte((short)(pc + 2))));
        System.out.print(instruction.name + "         ");
        System.out.print("A:" + String.format("%02X", regA) + " ");
        System.out.print("X:" + String.format("%02X", regX) + " ");
        System.out.print("Y:" + String.format("%02X", regY) + " ");
        System.out.print("P:" + String.format("%02X", regP) + " ");
        System.out.println("SP:" + String.format("%02X", regS));
    }

    @Override
    boolean addressIsMapped(short address) {
        return address == 0x4014;
    }

    @Override
    byte readByteFromDevice(short address) {
        return 0;
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        regOAMDMA = value;
        dmaCyclesLeft = cycleCount % 2 == 0 ? 513 : 514;
    }

    private enum StatusFlag {
        CARRY((byte)1),
        ZERO((byte)(1 << 1)),
        IRQ_DISABLE((byte)(1 << 2)),
        DECIMAL_MODE((byte)(1 << 3)),
        BREAK((byte)(1 << 4)),
        BIT5((byte)(1 << 5)),
        OVERFLOW((byte)(1 << 6)),
        NEGATIVE((byte)(1 << 7));

        public final byte bit;

        StatusFlag(byte bit) {
            this.bit = bit;
        }
    }

    private void setFlag(StatusFlag flag, boolean value) {
        if (value)
            regP |= flag.bit;
        else
            regP &= ~flag.bit;
    }

    private boolean getFlag(StatusFlag flag) {
        return (regP & flag.bit) != 0;
    }

    private enum AddressingMode {
        ACCUMULATOR,
        ABSOLUTE,
        ABSOLUTE_X,
        ABSOLUTE_Y,
        IMMEDIATE,
        IMPLIED,
        INDIRECT,
        INDIRECT_X,
        INDIRECT_Y,
        RELATIVE,
        ZEROPAGE,
        ZEROPAGE_X,
        ZEROPAGE_Y,
    }

    public void clockTick() {
        if (dmaCyclesLeft > 0) {
            if (dmaCyclesLeft <= 512) {
                if (dmaCyclesLeft % 2 == 0) {
                    int address = (regOAMDMA << 8) + 256 - (dmaCyclesLeft / 2);
                    dmaBuffer = addressSpace.readByte((short)address);
                } else
                    addressSpace.writeByte((short)0x2004, dmaBuffer);
            }
            dmaCyclesLeft--;
            return;
        }
        if (cyclesUntilNextInstruction <= 0) {
            // Check for interrupts, etc.
            if (requestNMI)
                serviceNMI();
            else {
                // short prevRegPC = regPC;
                final byte opcode = readByteAtPCAndIncrement();
                final Instruction instruction = instructionLookupTable[Byte.toUnsignedInt(opcode)];
                operandEffectiveAddress = getEffectiveAddress(instruction);
                cyclesUntilNextInstruction += instruction.cycles;
                // log(instruction, prevRegPC);
                instruction.operation.run();
            }
        }
        cyclesUntilNextInstruction--;
        cycleCount++;
    }

    public void reset() {
        regS -= 3;
        setFlag(StatusFlag.IRQ_DISABLE, true);
        regPC = (short)Byte.toUnsignedInt(addressSpace.readByte((short)0xFFFC));
        regPC += addressSpace.readByte((short)0xFFFD) << 8;
        cycleCount = 0;
        cyclesUntilNextInstruction = 7;
    }

    private void serviceNMI() {
        pushToStack((byte)((regPC & 0xFF00) >> 8));
        pushToStack((byte)(regPC & 0xFF));
        pushPToStack(false);
        regPC = (short)Byte.toUnsignedInt(addressSpace.readByte((short)0xFFFA));
        regPC += addressSpace.readByte((short)0xFFFB) << 8;
        requestNMI = false;
        cyclesUntilNextInstruction += 7;
    }

    private byte readByteAtPCAndIncrement() {
        return addressSpace.readByte(regPC++);
    }

    private short getEffectiveAddress(Instruction instruction) {
        short address = 0, indirectAddress = 0;
        short previousPage;
        isMemoryOperand = true;
        switch (instruction.addressingMode) {
            case IMMEDIATE:
                address = regPC;
                regPC++;
                break;
            case RELATIVE:
                address = readByteAtPCAndIncrement();
                previousPage = (short)(regPC & 0xFF00);
                address += regPC;
                branchDestinationPageCrossed = (short)(address & 0xFF00) != previousPage;
                break;
            case ABSOLUTE_X:
            case ABSOLUTE_Y:
                address = (short)Byte.toUnsignedInt(instruction.addressingMode
                        == AddressingMode.ABSOLUTE_X ? regX : regY);
            case ABSOLUTE:
                address += Byte.toUnsignedInt(readByteAtPCAndIncrement());
                if (address > 0xFF && instruction.cycles == 4)
                    cyclesUntilNextInstruction++;
                address += readByteAtPCAndIncrement() << 8;
                break;
            case INDIRECT:
                indirectAddress = (short)Byte.toUnsignedInt(readByteAtPCAndIncrement());
                indirectAddress += readByteAtPCAndIncrement() << 8;
                address = (short)Byte.toUnsignedInt(addressSpace.readByte(indirectAddress));
                short secondByteAddress = (short)(indirectAddress & 0xFF00 |
                        (indirectAddress + 1) & 0xFF);
                address += addressSpace.readByte(secondByteAddress) << 8;
                break;
            case INDIRECT_X:
                indirectAddress = (short)((readByteAtPCAndIncrement() + regX) & 0xFF);
                address = (short)Byte.toUnsignedInt(addressSpace.readByte(indirectAddress));
                address += addressSpace.readByte((short)((indirectAddress + 1) & 0xFF)) << 8;
                break;
            case INDIRECT_Y:
                indirectAddress = (short)Byte.toUnsignedInt(readByteAtPCAndIncrement());
                address = (short)Byte.toUnsignedInt(addressSpace.readByte(indirectAddress));
                address += addressSpace.readByte((short)((indirectAddress + 1) & 0xFF)) << 8;
                previousPage = (short)(address & 0xFF00);
                address += (short)Byte.toUnsignedInt(regY);
                if ((short)(address & 0xFF00) != previousPage && instruction.cycles == 5)
                    cyclesUntilNextInstruction++;
                break;
            case ZEROPAGE_X:
            case ZEROPAGE_Y:
                address = (short)Byte.toUnsignedInt(instruction.addressingMode
                        == AddressingMode.ZEROPAGE_X ? regX : regY);
            case ZEROPAGE:
                address += readByteAtPCAndIncrement();
                address &= 0xFF;
                break;
            default:    // IMPLIED, ACCUMULATOR
                isMemoryOperand = false;
        }
        return address;
    }

    private class Instruction {
        final String name;
        final AddressingMode addressingMode;
        final int cycles;
        final Runnable operation;

        public Instruction(String name, AddressingMode addressingMode,
                int cycles, Runnable operation) {
            this.name = name;
            this.addressingMode = addressingMode;
            this.cycles = cycles;
            this.operation = operation;
        }
    }

    private final Instruction undefinedInstruction = new Instruction("UNDEFINED",
            AddressingMode.IMPLIED, 1, () -> NOP());

    private final Instruction[] instructionLookupTable = new Instruction[] {
        // 0-
        new Instruction("BRK", AddressingMode.IMPLIED, 7,     () -> BRK()),
        new Instruction("ORA", AddressingMode.INDIRECT_X, 6,  () -> ORA()),
        undefinedInstruction,
        new Instruction("*SLO", AddressingMode.INDIRECT_X, 8, () -> SLO()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE, 3,   () -> NOP()),
        new Instruction("ORA", AddressingMode.ZEROPAGE, 3,    () -> ORA()),
        new Instruction("ASL", AddressingMode.ZEROPAGE, 5,    () -> ASL()),
        new Instruction("*SLO", AddressingMode.ZEROPAGE, 5,   () -> SLO()),
        new Instruction("PHP", AddressingMode.IMPLIED, 3,     () -> PHP()),
        new Instruction("ORA", AddressingMode.IMMEDIATE, 2,   () -> ORA()),
        new Instruction("ASL", AddressingMode.ACCUMULATOR, 2, () -> ASL()),
        new Instruction("*ANC", AddressingMode.IMMEDIATE, 2,  () -> ANC()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE, 4,   () -> NOP()),
        new Instruction("ORA", AddressingMode.ABSOLUTE, 4,    () -> ORA()),
        new Instruction("ASL", AddressingMode.ABSOLUTE, 6,    () -> ASL()),
        new Instruction("*SLO", AddressingMode.ABSOLUTE, 6,   () -> SLO()),
        // 1-
        new Instruction("BPL", AddressingMode.RELATIVE, 2,    () -> BPL()),
        new Instruction("ORA", AddressingMode.INDIRECT_Y, 5,  () -> ORA()),
        undefinedInstruction,
        new Instruction("*SLO", AddressingMode.INDIRECT_Y, 8, () -> SLO()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("ORA", AddressingMode.ZEROPAGE_X, 4,  () -> ORA()),
        new Instruction("ASL", AddressingMode.ZEROPAGE_X, 6,  () -> ASL()),
        new Instruction("*SLO", AddressingMode.ZEROPAGE_X, 6, () -> SLO()),
        new Instruction("CLC", AddressingMode.IMPLIED, 2,     () -> CLC()),
        new Instruction("ORA", AddressingMode.ABSOLUTE_Y, 4,  () -> ORA()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*SLO", AddressingMode.ABSOLUTE_Y, 7, () -> SLO()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("ORA", AddressingMode.ABSOLUTE_X, 4,  () -> ORA()),
        new Instruction("ASL", AddressingMode.ABSOLUTE_X, 7,  () -> ASL()),
        new Instruction("*SLO", AddressingMode.ABSOLUTE_X, 7, () -> SLO()),
        // 2-
        new Instruction("JSR", AddressingMode.ABSOLUTE, 6,    () -> JSR()),
        new Instruction("AND", AddressingMode.INDIRECT_X, 6,  () -> AND()),
        undefinedInstruction,
        new Instruction("*RLA", AddressingMode.INDIRECT_X, 8, () -> RLA()),
        new Instruction("BIT", AddressingMode.ZEROPAGE, 3,    () -> BIT()),
        new Instruction("AND", AddressingMode.ZEROPAGE, 3,    () -> AND()),
        new Instruction("ROL", AddressingMode.ZEROPAGE, 5,    () -> ROL()),
        new Instruction("*RLA", AddressingMode.ZEROPAGE, 5,   () -> RLA()),
        new Instruction("PLP", AddressingMode.IMPLIED, 4,     () -> PLP()),
        new Instruction("AND", AddressingMode.IMMEDIATE, 2,   () -> AND()),
        new Instruction("ROL", AddressingMode.ACCUMULATOR, 2, () -> ROL()),
        new Instruction("*ANC", AddressingMode.IMMEDIATE, 2,  () -> ANC()),
        new Instruction("BIT", AddressingMode.ABSOLUTE, 4,    () -> BIT()),
        new Instruction("AND", AddressingMode.ABSOLUTE, 4,    () -> AND()),
        new Instruction("ROL", AddressingMode.ABSOLUTE, 6,    () -> ROL()),
        new Instruction("*RLA", AddressingMode.ABSOLUTE, 6,   () -> RLA()),
        // 3-
        new Instruction("BMI", AddressingMode.RELATIVE, 2,    () -> BMI()),
        new Instruction("AND", AddressingMode.INDIRECT_Y, 5,  () -> AND()),
        undefinedInstruction,
        new Instruction("*RLA", AddressingMode.INDIRECT_Y, 8, () -> RLA()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("AND", AddressingMode.ZEROPAGE_X, 4,  () -> AND()),
        new Instruction("ROL", AddressingMode.ZEROPAGE_X, 6,  () -> ROL()),
        new Instruction("*RLA", AddressingMode.ZEROPAGE_X, 6, () -> RLA()),
        new Instruction("SEC", AddressingMode.IMPLIED, 2,     () -> SEC()),
        new Instruction("AND", AddressingMode.ABSOLUTE_Y, 4,  () -> AND()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*RLA", AddressingMode.ABSOLUTE_Y, 7, () -> RLA()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("AND", AddressingMode.ABSOLUTE_X, 4,  () -> AND()),
        new Instruction("ROL", AddressingMode.ABSOLUTE_X, 7,  () -> ROL()),
        new Instruction("*RLA", AddressingMode.ABSOLUTE_X, 7, () -> RLA()),
        // 4-
        new Instruction("RTI", AddressingMode.IMPLIED, 6,     () -> RTI()),
        new Instruction("EOR", AddressingMode.INDIRECT_X, 6,  () -> EOR()),
        undefinedInstruction,
        new Instruction("*SRE", AddressingMode.INDIRECT_X, 8, () -> SRE()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE, 3,   () -> NOP()),
        new Instruction("EOR", AddressingMode.ZEROPAGE, 3,    () -> EOR()),
        new Instruction("LSR", AddressingMode.ZEROPAGE, 5,    () -> LSR()),
        new Instruction("*SRE", AddressingMode.ZEROPAGE, 5,   () -> SRE()),
        new Instruction("PHA", AddressingMode.IMPLIED, 3,     () -> PHA()),
        new Instruction("EOR", AddressingMode.IMMEDIATE, 2,   () -> EOR()),
        new Instruction("LSR", AddressingMode.ACCUMULATOR, 2, () -> LSR()),
        new Instruction("*ALR", AddressingMode.IMMEDIATE, 2,  () -> ALR()),
        new Instruction("JMP", AddressingMode.ABSOLUTE, 3,    () -> JMP()),
        new Instruction("EOR", AddressingMode.ABSOLUTE, 4,    () -> EOR()),
        new Instruction("LSR", AddressingMode.ABSOLUTE, 6,    () -> LSR()),
        new Instruction("*SRE", AddressingMode.ABSOLUTE, 6,   () -> SRE()),
        // 5-
        new Instruction("BVC", AddressingMode.RELATIVE, 2,    () -> BVC()),
        new Instruction("EOR", AddressingMode.INDIRECT_Y, 5,  () -> EOR()),
        undefinedInstruction,
        new Instruction("*SRE", AddressingMode.INDIRECT_Y, 8, () -> SRE()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("EOR", AddressingMode.ZEROPAGE_X, 4,  () -> EOR()),
        new Instruction("LSR", AddressingMode.ZEROPAGE_X, 6,  () -> LSR()),
        new Instruction("*SRE", AddressingMode.ZEROPAGE_X, 6, () -> SRE()),
        new Instruction("CLI", AddressingMode.IMPLIED, 2,     () -> CLI()),
        new Instruction("EOR", AddressingMode.ABSOLUTE_Y, 4,  () -> EOR()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*SRE", AddressingMode.ABSOLUTE_Y, 7, () -> SRE()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("EOR", AddressingMode.ABSOLUTE_X, 4,  () -> EOR()),
        new Instruction("LSR", AddressingMode.ABSOLUTE_X, 7,  () -> LSR()),
        new Instruction("*SRE", AddressingMode.ABSOLUTE_X, 7, () -> SRE()),
        // 6-
        new Instruction("RTS", AddressingMode.IMPLIED, 6,     () -> RTS()),
        new Instruction("ADC", AddressingMode.INDIRECT_X, 6,  () -> ADC()),
        undefinedInstruction,
        new Instruction("*RRA", AddressingMode.INDIRECT_X, 8, () -> RRA()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE, 3,   () -> NOP()),
        new Instruction("ADC", AddressingMode.ZEROPAGE, 3,    () -> ADC()),
        new Instruction("ROR", AddressingMode.ZEROPAGE, 5,    () -> ROR()),
        new Instruction("*RRA", AddressingMode.ZEROPAGE, 5,   () -> RRA()),
        new Instruction("PLA", AddressingMode.IMPLIED, 4,     () -> PLA()),
        new Instruction("ADC", AddressingMode.IMMEDIATE, 2,   () -> ADC()),
        new Instruction("ROR", AddressingMode.ACCUMULATOR, 2, () -> ROR()),
        undefinedInstruction,
        new Instruction("JMP", AddressingMode.INDIRECT, 5,    () -> JMP()),
        new Instruction("ADC", AddressingMode.ABSOLUTE, 4,    () -> ADC()),
        new Instruction("ROR", AddressingMode.ABSOLUTE, 6,    () -> ROR()),
        new Instruction("*RRA", AddressingMode.ABSOLUTE, 6,   () -> RRA()),
        // 7-
        new Instruction("BVS", AddressingMode.RELATIVE, 2,    () -> BVS()),
        new Instruction("ADC", AddressingMode.INDIRECT_Y, 5,  () -> ADC()),
        undefinedInstruction,
        new Instruction("*RRA", AddressingMode.INDIRECT_Y, 8, () -> RRA()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("ADC", AddressingMode.ZEROPAGE_X, 4,  () -> ADC()),
        new Instruction("ROR", AddressingMode.ZEROPAGE_X, 6,  () -> ROR()),
        new Instruction("*RRA", AddressingMode.ZEROPAGE_X, 6, () -> RRA()),
        new Instruction("SEI", AddressingMode.IMPLIED, 2,     () -> SEI()),
        new Instruction("ADC", AddressingMode.ABSOLUTE_Y, 4,  () -> ADC()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*RRA", AddressingMode.ABSOLUTE_Y, 7, () -> RRA()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("ADC", AddressingMode.ABSOLUTE_X, 4,  () -> ADC()),
        new Instruction("ROR", AddressingMode.ABSOLUTE_X, 7,  () -> ROR()),
        new Instruction("*RRA", AddressingMode.ABSOLUTE_X, 7, () -> RRA()),
        // 8-
        new Instruction("*NOP", AddressingMode.IMMEDIATE, 2,  () -> NOP()),
        new Instruction("STA", AddressingMode.INDIRECT_X, 6,  () -> STA()),
        undefinedInstruction,
        new Instruction("*SAX", AddressingMode.INDIRECT_X, 6, () -> SAX()),
        new Instruction("STY", AddressingMode.ZEROPAGE, 3,    () -> STY()),
        new Instruction("STA", AddressingMode.ZEROPAGE, 3,    () -> STA()),
        new Instruction("STX", AddressingMode.ZEROPAGE, 3,    () -> STX()),
        new Instruction("*SAX", AddressingMode.ZEROPAGE, 3,   () -> SAX()),
        new Instruction("DEY", AddressingMode.IMPLIED, 2,     () -> DEY()),
        new Instruction("*NOP", AddressingMode.IMMEDIATE, 2,  () -> NOP()),
        new Instruction("TXA", AddressingMode.IMPLIED, 2,     () -> TXA()),
        undefinedInstruction,
        new Instruction("STY", AddressingMode.ABSOLUTE, 4,    () -> STY()),
        new Instruction("STA", AddressingMode.ABSOLUTE, 4,    () -> STA()),
        new Instruction("STX", AddressingMode.ABSOLUTE, 4,    () -> STX()),
        new Instruction("*SAX", AddressingMode.ABSOLUTE, 4,   () -> SAX()),
        // 9-
        new Instruction("BCC", AddressingMode.RELATIVE, 2,    () -> BCC()),
        new Instruction("STA", AddressingMode.INDIRECT_Y, 6,  () -> STA()),
        undefinedInstruction, undefinedInstruction,
        new Instruction("STY", AddressingMode.ZEROPAGE_X, 4,  () -> STY()),
        new Instruction("STA", AddressingMode.ZEROPAGE_X, 4,  () -> STA()),
        new Instruction("STX", AddressingMode.ZEROPAGE_Y, 4,  () -> STX()),
        new Instruction("*SAX", AddressingMode.ZEROPAGE_Y, 4, () -> SAX()),
        new Instruction("TYA", AddressingMode.IMPLIED, 2,     () -> TYA()),
        new Instruction("STA", AddressingMode.ABSOLUTE_Y, 5,  () -> STA()),
        new Instruction("TXS", AddressingMode.IMPLIED, 2,     () -> TXS()),
        undefinedInstruction, undefinedInstruction,
        new Instruction("STA", AddressingMode.ABSOLUTE_X, 5,  () -> STA()),
        undefinedInstruction, undefinedInstruction,
        // A-
        new Instruction("LDY", AddressingMode.IMMEDIATE, 2,   () -> LDY()),
        new Instruction("LDA", AddressingMode.INDIRECT_X, 6,  () -> LDA()),
        new Instruction("LDX", AddressingMode.IMMEDIATE, 2,   () -> LDX()),
        new Instruction("*LAX", AddressingMode.INDIRECT_X, 6, () -> LAX()),
        new Instruction("LDY", AddressingMode.ZEROPAGE, 3,    () -> LDY()),
        new Instruction("LDA", AddressingMode.ZEROPAGE, 3,    () -> LDA()),
        new Instruction("LDX", AddressingMode.ZEROPAGE, 3,    () -> LDX()),
        new Instruction("*LAX", AddressingMode.ZEROPAGE, 3,   () -> LAX()),
        new Instruction("TAY", AddressingMode.IMPLIED, 2,     () -> TAY()),
        new Instruction("LDA", AddressingMode.IMMEDIATE, 2,   () -> LDA()),
        new Instruction("TAX", AddressingMode.IMPLIED, 2,     () -> TAX()),
        undefinedInstruction,
        new Instruction("LDY", AddressingMode.ABSOLUTE, 4,    () -> LDY()),
        new Instruction("LDA", AddressingMode.ABSOLUTE, 4,    () -> LDA()),
        new Instruction("LDX", AddressingMode.ABSOLUTE, 4,    () -> LDX()),
        new Instruction("*LAX", AddressingMode.ABSOLUTE, 4,   () -> LAX()),
        // B-
        new Instruction("BCS", AddressingMode.RELATIVE, 2,    () -> BCS()),
        new Instruction("LDA", AddressingMode.INDIRECT_Y, 5,  () -> LDA()),
        undefinedInstruction,
        new Instruction("*LAX", AddressingMode.INDIRECT_Y, 5, () -> LAX()),
        new Instruction("LDY", AddressingMode.ZEROPAGE_X, 4,  () -> LDY()),
        new Instruction("LDA", AddressingMode.ZEROPAGE_X, 4,  () -> LDA()),
        new Instruction("LDX", AddressingMode.ZEROPAGE_Y, 4,  () -> LDX()),
        new Instruction("*LAX", AddressingMode.ZEROPAGE_Y, 4, () -> LAX()),
        new Instruction("CLV", AddressingMode.IMPLIED, 2,     () -> CLV()),
        new Instruction("LDA", AddressingMode.ABSOLUTE_Y, 4,  () -> LDA()),
        new Instruction("TSX", AddressingMode.IMPLIED, 2,     () -> TSX()),
        undefinedInstruction,
        new Instruction("LDY", AddressingMode.ABSOLUTE_X, 4,  () -> LDY()),
        new Instruction("LDA", AddressingMode.ABSOLUTE_X, 4,  () -> LDA()),
        new Instruction("LDX", AddressingMode.ABSOLUTE_Y, 4,  () -> LDX()),
        new Instruction("*LAX", AddressingMode.ABSOLUTE_Y, 4, () -> LAX()),
        // C-
        new Instruction("CPY", AddressingMode.IMMEDIATE, 2,   () -> CPY()),
        new Instruction("CMP", AddressingMode.INDIRECT_X, 6,  () -> CMP()),
        new Instruction("*NOP", AddressingMode.IMMEDIATE, 2,  () -> NOP()),
        new Instruction("*DCP", AddressingMode.INDIRECT_X, 8, () -> DCP()),
        new Instruction("CPY", AddressingMode.ZEROPAGE, 3,    () -> CPY()),
        new Instruction("CMP", AddressingMode.ZEROPAGE, 3,    () -> CMP()),
        new Instruction("DEC", AddressingMode.ZEROPAGE, 5,    () -> DEC()),
        new Instruction("*DCP", AddressingMode.ZEROPAGE, 5,   () -> DCP()),
        new Instruction("INY", AddressingMode.IMPLIED, 2,     () -> INY()),
        new Instruction("CMP", AddressingMode.IMMEDIATE, 2,   () -> CMP()),
        new Instruction("DEX", AddressingMode.IMPLIED, 2,     () -> DEX()),
        undefinedInstruction,
        new Instruction("CPY", AddressingMode.ABSOLUTE, 4,    () -> CPY()),
        new Instruction("CMP", AddressingMode.ABSOLUTE, 4,    () -> CMP()),
        new Instruction("DEC", AddressingMode.ABSOLUTE, 6,    () -> DEC()),
        new Instruction("*DCP", AddressingMode.ABSOLUTE, 6,   () -> DCP()),
        // D-
        new Instruction("BNE", AddressingMode.RELATIVE, 2,    () -> BNE()),
        new Instruction("CMP", AddressingMode.INDIRECT_Y, 5,  () -> CMP()),
        undefinedInstruction,
        new Instruction("*DCP", AddressingMode.INDIRECT_Y, 8, () -> DCP()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("CMP", AddressingMode.ZEROPAGE_X, 4,  () -> CMP()),
        new Instruction("DEC", AddressingMode.ZEROPAGE_X, 6,  () -> DEC()),
        new Instruction("*DCP", AddressingMode.ZEROPAGE_X, 6, () -> DCP()),
        new Instruction("CLD", AddressingMode.IMPLIED, 2,     () -> CLD()),
        new Instruction("CMP", AddressingMode.ABSOLUTE_Y, 4,  () -> CMP()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*DCP", AddressingMode.ABSOLUTE_Y, 7, () -> DCP()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("CMP", AddressingMode.ABSOLUTE_X, 4,  () -> CMP()),
        new Instruction("DEC", AddressingMode.ABSOLUTE_X, 7,  () -> DEC()),
        new Instruction("*DCP", AddressingMode.ABSOLUTE_X, 7, () -> DCP()),
        // E-
        new Instruction("CPX", AddressingMode.IMMEDIATE, 2,   () -> CPX()),
        new Instruction("SBC", AddressingMode.INDIRECT_X, 6,  () -> SBC()),
        new Instruction("*NOP", AddressingMode.IMMEDIATE, 2,  () -> NOP()),
        new Instruction("*ISC", AddressingMode.INDIRECT_X, 8, () -> ISC()),
        new Instruction("CPX", AddressingMode.ZEROPAGE, 3,    () -> CPX()),
        new Instruction("SBC", AddressingMode.ZEROPAGE, 3,    () -> SBC()),
        new Instruction("INC", AddressingMode.ZEROPAGE, 5,    () -> INC()),
        new Instruction("*ISC", AddressingMode.ZEROPAGE, 5,   () -> ISC()),
        new Instruction("INX", AddressingMode.IMPLIED, 2,     () -> INX()),
        new Instruction("SBC", AddressingMode.IMMEDIATE, 2,   () -> SBC()),
        new Instruction("NOP", AddressingMode.IMPLIED, 2,     () -> NOP()),
        new Instruction("*SBC", AddressingMode.IMMEDIATE, 2,  () -> SBC()),
        new Instruction("CPX", AddressingMode.ABSOLUTE, 4,    () -> CPX()),
        new Instruction("SBC", AddressingMode.ABSOLUTE, 4,    () -> SBC()),
        new Instruction("INC", AddressingMode.ABSOLUTE, 6,    () -> INC()),
        new Instruction("*ISC", AddressingMode.ABSOLUTE, 6,   () -> ISC()),
        // F-
        new Instruction("BEQ", AddressingMode.RELATIVE, 2,    () -> BEQ()),
        new Instruction("SBC", AddressingMode.INDIRECT_Y, 5,  () -> SBC()),
        undefinedInstruction,
        new Instruction("*ISC", AddressingMode.INDIRECT_Y, 8, () -> ISC()),
        new Instruction("*NOP", AddressingMode.ZEROPAGE_X, 4, () -> NOP()),
        new Instruction("SBC", AddressingMode.ZEROPAGE_X, 4,  () -> SBC()),
        new Instruction("INC", AddressingMode.ZEROPAGE_X, 6,  () -> INC()),
        new Instruction("*ISC", AddressingMode.ZEROPAGE_X, 6, () -> ISC()),
        new Instruction("SED", AddressingMode.IMPLIED, 2,     () -> SED()),
        new Instruction("SBC", AddressingMode.ABSOLUTE_Y, 4,  () -> SBC()),
        new Instruction("*NOP", AddressingMode.IMPLIED, 2,    () -> NOP()),
        new Instruction("*ISC", AddressingMode.ABSOLUTE_Y, 7, () -> ISC()),
        new Instruction("*NOP", AddressingMode.ABSOLUTE_X, 4, () -> NOP()),
        new Instruction("SBC", AddressingMode.ABSOLUTE_X, 4,  () -> SBC()),
        new Instruction("INC", AddressingMode.ABSOLUTE_X, 7,  () -> INC()),
        new Instruction("*ISC", AddressingMode.ABSOLUTE_X, 7, () -> ISC()),
    };

    private void pushToStack(byte value) {
        addressSpace.writeByte((short)((regS & 0xFF) | 0x100), value);
        regS--;
    }

    private byte pullFromStack() {
        regS++;
        return addressSpace.readByte((short)((regS | 0x100) & 0x1FF));
    }

    private void pushPToStack(boolean setBreakBit) {
        byte pushedP = regP;
        pushedP |= CPU.StatusFlag.BIT5.bit;
        if (setBreakBit)
            pushedP |= CPU.StatusFlag.BREAK.bit;
        pushToStack(pushedP);
    }

    private void pullPFromStack() {
        boolean prevBreakFlag = getFlag(StatusFlag.BREAK);
        boolean prevBit5Flag = getFlag(StatusFlag.BIT5);
        regP = pullFromStack();
        setFlag(StatusFlag.BREAK, prevBreakFlag);
        setFlag(StatusFlag.BIT5, prevBit5Flag);
    }

    private void ADC() {
        final byte operand = addressSpace.readByte(operandEffectiveAddress);
        int sum = Byte.toUnsignedInt(regA) + Byte.toUnsignedInt(operand);
        if (getFlag(StatusFlag.CARRY))
            sum++;
        final byte previousRegA = regA;
        regA = (byte)sum;
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.CARRY, (sum & 0x100) != 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
        setFlag(StatusFlag.OVERFLOW, (operand & 0x80) == (previousRegA & 0x80) &&
                (sum & 0x80) != (operand & 0x80));
    }

    private void ALR() {
        AND();
        LSR();
    }

    private void ANC() {
        AND();
        setFlag(StatusFlag.CARRY, getFlag(StatusFlag.NEGATIVE));
    }

    private void AND() {
        regA &= addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void ASL() {
        int result;
        if (isMemoryOperand) {
            result = Byte.toUnsignedInt(addressSpace
                    .readByte(operandEffectiveAddress));
            result <<= 1;
            addressSpace.writeByte(operandEffectiveAddress, (byte)result);
        } else {
            result = Byte.toUnsignedInt(regA) << 1;
            regA = (byte)result;
        }
        setFlag(StatusFlag.CARRY, (result & 0x100) != 0);
        setFlag(StatusFlag.ZERO, (result & 0xFF) == 0);
        setFlag(StatusFlag.NEGATIVE, (result & 0x80) != 0);
    }

    private void BCC() {
        if (!getFlag(StatusFlag.CARRY)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BCS() {
        if (getFlag(StatusFlag.CARRY)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BEQ() {
        if (getFlag(StatusFlag.ZERO)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BIT() {
        final byte memoryOperand = addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, (regA & memoryOperand) == 0);
        setFlag(StatusFlag.OVERFLOW, (memoryOperand & 0x40) != 0);
        setFlag(StatusFlag.NEGATIVE, (memoryOperand & 0x80) != 0);
    }

    private void BMI() {
        if (getFlag(StatusFlag.NEGATIVE)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BNE() {
        if (!getFlag(StatusFlag.ZERO)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BPL() {
        if (!getFlag(StatusFlag.NEGATIVE)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BRK() {
        final short pushedPC = (short)(regPC + 1);
        pushToStack((byte)((pushedPC & 0xFF00) >> 8));
        pushToStack((byte)(pushedPC & 0xFF));
        pushPToStack(true);
        setFlag(StatusFlag.IRQ_DISABLE, true);
        regPC = (short)Byte.toUnsignedInt(addressSpace.readByte((short)0xFFFE));
        regPC += addressSpace.readByte((short)0xFFFF) << 8;
    }

    private void BVC() {
        if (!getFlag(StatusFlag.OVERFLOW)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void BVS() {
        if (getFlag(StatusFlag.OVERFLOW)) {
            regPC = operandEffectiveAddress;
            cyclesUntilNextInstruction += branchDestinationPageCrossed ? 2 : 1;
        }
    }

    private void CLC() {
        setFlag(StatusFlag.CARRY, false);
    }

    private void CLD() {
        setFlag(StatusFlag.DECIMAL_MODE, false);
    }

    private void CLI() {
        setFlag(StatusFlag.IRQ_DISABLE, false);
    }

    private void CLV() {
        setFlag(StatusFlag.OVERFLOW, false);
    }

    private void CMP() {
        final byte operand = addressSpace.readByte(operandEffectiveAddress);
        final int result = Byte.compareUnsigned(regA, operand);
        setFlag(StatusFlag.CARRY, result >= 0);
        setFlag(StatusFlag.ZERO, result == 0);
        setFlag(StatusFlag.NEGATIVE, ((regA - operand) & 0x80) != 0);
    }

    private void CPX() {
        final byte operand = addressSpace.readByte(operandEffectiveAddress);
        final int result = Byte.compareUnsigned(regX, operand);
        setFlag(StatusFlag.CARRY, result >= 0);
        setFlag(StatusFlag.ZERO, result == 0);
        setFlag(StatusFlag.NEGATIVE, ((regX - operand) & 0x80) != 0);
    }

    private void CPY() {
        final byte operand = addressSpace.readByte(operandEffectiveAddress);
        final int result = Byte.compareUnsigned(regY, operand);
        setFlag(StatusFlag.CARRY, result >= 0);
        setFlag(StatusFlag.ZERO, result == 0);
        setFlag(StatusFlag.NEGATIVE, ((regY - operand) & 0x80) != 0);
    }

    private void DCP() {
        byte operand = addressSpace.readByte(operandEffectiveAddress);
        operand--;
        addressSpace.writeByte(operandEffectiveAddress, operand);
        CMP();
    }

    private void DEC() {
        byte operand = addressSpace.readByte(operandEffectiveAddress);
        operand--;
        addressSpace.writeByte(operandEffectiveAddress, operand);
        setFlag(StatusFlag.ZERO, operand == 0);
        setFlag(StatusFlag.NEGATIVE, (operand & 0x80) != 0);
    }

    private void DEX() {
        regX--;
        setFlag(StatusFlag.ZERO, regX == 0);
        setFlag(StatusFlag.NEGATIVE, (regX & 0x80) != 0);
    }

    private void DEY() {
        regY--;
        setFlag(StatusFlag.ZERO, regY == 0);
        setFlag(StatusFlag.NEGATIVE, (regY & 0x80) != 0);
    }

    private void EOR() {
        regA ^= addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void INC() {
        byte operand = addressSpace.readByte(operandEffectiveAddress);
        operand++;
        addressSpace.writeByte(operandEffectiveAddress, operand);
        setFlag(StatusFlag.ZERO, operand == 0);
        setFlag(StatusFlag.NEGATIVE, (operand & 0x80) != 0);
    }

    private void INX() {
        regX++;
        setFlag(StatusFlag.ZERO, regX == 0);
        setFlag(StatusFlag.NEGATIVE, (regX & 0x80) != 0);
    }

    private void INY() {
        regY++;
        setFlag(StatusFlag.ZERO, regY == 0);
        setFlag(StatusFlag.NEGATIVE, (regY & 0x80) != 0);
    }

    private void ISC() {
        byte operand = addressSpace.readByte(operandEffectiveAddress);
        operand++;
        addressSpace.writeByte(operandEffectiveAddress, operand);
        SBC();
    }

    private void JMP() {
        regPC = operandEffectiveAddress;
    }

    private void JSR() {
        regPC--;
        pushToStack((byte)((regPC & 0xFF00) >> 8));
        pushToStack((byte)(regPC & 0xFF));
        regPC = operandEffectiveAddress;
    }

    private void LAX() {
        LDA();
        regX = regA;
    }

    private void LDA() {
        regA = addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void LDX() {
        regX = addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regX == 0);
        setFlag(StatusFlag.NEGATIVE, (regX & 0x80) != 0);
    }

    private void LDY() {
        regY = addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regY == 0);
        setFlag(StatusFlag.NEGATIVE, (regY & 0x80) != 0);
    }

    private void LSR() {
        int result;
        boolean previousBitZero;
        if (isMemoryOperand) {
            result = Byte.toUnsignedInt(addressSpace
                    .readByte(operandEffectiveAddress));
            previousBitZero = (result & 1) != 0;
            result >>>= 1;
            addressSpace.writeByte(operandEffectiveAddress, (byte)result);
        } else {
            previousBitZero = (regA & 1) != 0;
            result = Byte.toUnsignedInt(regA) >>> 1;
            regA = (byte)result;
        }
        setFlag(StatusFlag.CARRY, previousBitZero);
        setFlag(StatusFlag.ZERO, (result & 0xFF) == 0);
        setFlag(StatusFlag.NEGATIVE, (result & 0x80) != 0);
    }

    private void NOP() {

    }

    private void ORA() {
        regA |= addressSpace.readByte(operandEffectiveAddress);
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void PHA() {
        pushToStack(regA);
    }

    private void PHP() {
        pushPToStack(true);
    }

    private void PLA() {
        regA = pullFromStack();
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void PLP() {
        pullPFromStack();
    }

    private void RLA() {
        ROL();
        AND();
    }

    private void ROL() {
        int result;
        if (isMemoryOperand) {
            result = Byte.toUnsignedInt(addressSpace
                    .readByte(operandEffectiveAddress));
            result <<= 1;
            result |= getFlag(StatusFlag.CARRY) ? 1 : 0;
            addressSpace.writeByte(operandEffectiveAddress, (byte)result);
        } else {
            result = Byte.toUnsignedInt(regA) << 1;
            result |= getFlag(StatusFlag.CARRY) ? 1 : 0;
            regA = (byte)result;
        }
        setFlag(StatusFlag.CARRY, (result & 0x100) != 0);
        setFlag(StatusFlag.ZERO, (result & 0xFF) == 0);
        setFlag(StatusFlag.NEGATIVE, (result & 0x80) != 0);
    }

    private void ROR() {
        int result;
        boolean previousBitZero;
        if (isMemoryOperand) {
            result = Byte.toUnsignedInt(addressSpace
                    .readByte(operandEffectiveAddress));
            previousBitZero = (result & 1) != 0;
            result >>>= 1;
            result |= getFlag(StatusFlag.CARRY) ? 0x80 : 0;
            addressSpace.writeByte(operandEffectiveAddress, (byte)result);
        } else {
            previousBitZero = (regA & 1) != 0;
            result = Byte.toUnsignedInt(regA) >>> 1;
            result |= getFlag(StatusFlag.CARRY) ? 0x80 : 0;
            regA = (byte)result;
        }
        setFlag(StatusFlag.CARRY, previousBitZero);
        setFlag(StatusFlag.ZERO, (result & 0xFF) == 0);
        setFlag(StatusFlag.NEGATIVE, (result & 0x80) != 0);
    }

    private void RRA() {
        ROR();
        ADC();
    }

    private void RTI() {
        pullPFromStack();
        regPC = (short)Byte.toUnsignedInt(pullFromStack());
        regPC += pullFromStack() << 8;
    }

    private void RTS() {
        regPC = (short)Byte.toUnsignedInt(pullFromStack());
        regPC += pullFromStack() << 8;
        regPC++;
    }

    private void SAX() {
        addressSpace.writeByte(operandEffectiveAddress, (byte)(regA & regX));
    }

    private void SBC() {
        final byte operand = addressSpace.readByte(operandEffectiveAddress);
        int result = Byte.toUnsignedInt(regA) - Byte.toUnsignedInt(operand);
        if (!getFlag(StatusFlag.CARRY))
            result--;
        final byte previousRegA = regA;
        regA = (byte)result;
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.CARRY, result >= 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
        setFlag(StatusFlag.OVERFLOW, (operand & 0x80) != (previousRegA & 0x80) &&
                (result & 0x80) != (previousRegA & 0x80));
    }

    private void SEC() {
        setFlag(StatusFlag.CARRY, true);
    }

    private void SED() {
        setFlag(StatusFlag.DECIMAL_MODE, true);
    }

    private void SEI() {
        setFlag(StatusFlag.IRQ_DISABLE, true);
    }

    private void SLO() {
        ASL();
        ORA();
    }

    private void SRE() {
        LSR();
        EOR();
    }

    private void STA() {
        addressSpace.writeByte(operandEffectiveAddress, regA);
    }

    private void STX() {
        addressSpace.writeByte(operandEffectiveAddress, regX);
    }

    private void STY() {
        addressSpace.writeByte(operandEffectiveAddress, regY);
    }

    private void TAX() {
        regX = regA;
        setFlag(StatusFlag.ZERO, regX == 0);
        setFlag(StatusFlag.NEGATIVE, (regX & 0x80) != 0);
    }

    private void TAY() {
        regY = regA;
        setFlag(StatusFlag.ZERO, regY == 0);
        setFlag(StatusFlag.NEGATIVE, (regY & 0x80) != 0);
    }

    private void TSX() {
        regX = regS;
        setFlag(StatusFlag.ZERO, regX == 0);
        setFlag(StatusFlag.NEGATIVE, (regX & 0x80) != 0);
    }

    private void TXA() {
        regA = regX;
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }

    private void TXS() {
        regS = regX;
    }

    private void TYA() {
        regA = regY;
        setFlag(StatusFlag.ZERO, regA == 0);
        setFlag(StatusFlag.NEGATIVE, (regA & 0x80) != 0);
    }
}
