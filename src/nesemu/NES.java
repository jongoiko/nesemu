package nesemu;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class NES {
    private final AddressSpace addressSpace;
    private final CPU cpu;
    private final PPU ppu;
    private final RAM ram;
    private Cartridge cartridge;
    public final Controller controller;

    public NES(String cartridgeFilePath) throws IOException {
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
        cpu.reset();
        ppu.reset();
    }

    public void exchangeCartridge(String cartridgeFilePath) throws IOException {
        cartridge = Cartridge.fromINESFile(cartridgeFilePath);
        ppu.cartridge = cartridge;
        reset();
    }

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
