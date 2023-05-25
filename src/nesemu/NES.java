package nesemu;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;

public class NES implements Serializable {
    private final AddressSpace addressSpace;
    private final CPU cpu;
    private final PPU ppu;
    private final RAM ram;
    public Cartridge cartridge;
    public final Controller controller;

    public NES(String cartridgeFilePath) throws IOException,
            UnsupportedMapperException, IllegalArgumentException {
        addressSpace = new AddressSpace();
        cpu = new CPU();
        ram = new RAM();
        cartridge = Cartridge.fromINESFile(cartridgeFilePath);
        ppu = new PPU(cartridge);
        controller = new Controller();
        addressSpace.addDevice(cartridge);
        addressSpace.addDevice(ppu);
        addressSpace.addDevice(ram);
        addressSpace.addDevice(controller);
        addressSpace.addDevice(cpu);
        cpu.reset();
    }

    public void reset() {
        ppu.reset();
        cpu.reset();
        cartridge.reset();
    }

    public void exchangeCartridge(String cartridgeFilePath)
            throws IOException, UnsupportedMapperException {
        Cartridge newCartridge = Cartridge.fromINESFile(cartridgeFilePath);
        addressSpace.removeDevice(cartridge);
        cartridge = newCartridge;
        addressSpace.addDevice(cartridge);
        ppu.cartridge = cartridge;
        reset();
    }

    // The PPU's clock runs at three times the speed of the CPU's clock. See
    // https://www.nesdev.org/wiki/Cycle_reference_chart
    public void runUntilFrameReady(BufferedImage img) {
        while (!ppu.isFrameReady) {
            ppu.clockTick(img, cpu);
            ppu.clockTick(img, cpu);
            ppu.clockTick(img, cpu);
            cpu.clockTick();
        }
        ppu.isFrameReady = false;
    }
}
