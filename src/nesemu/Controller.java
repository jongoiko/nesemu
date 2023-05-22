package nesemu;

import java.awt.event.KeyEvent;

/* The state of the controllers is read by games through addresses 0x4016 and
 * 0x4017. When a byte with bit 0 set and then one with bit 0 clear are
 * written in sequence to 0x4016, the states of the 8 buttons are latched. Then,
 * the game may read the buttons bit by bit through the ports (0x4016 for player
 * 1 and 0x4017 for player 2). See https://www.nesdev.org/wiki/Standard_controller
*/

public class Controller extends MemoryMapped {

    /* To allow communicating button presses to the emulator on a frame-by-frame
     * basis, isPressedLocally is copied to the appropriate field (player 1 or
     * player two) with commitButtonStates.
    */
    public enum Button {
        BUTTON_A(KeyEvent.VK_X, (byte)1),
        BUTTON_B(KeyEvent.VK_Z, (byte)(1 << 1)),
        BUTTON_SELECT(KeyEvent.VK_SHIFT, (byte)(1 << 2)),
        BUTTON_START(KeyEvent.VK_ENTER, (byte)(1 << 3)),
        BUTTON_UP(KeyEvent.VK_UP, (byte)(1 << 4)),
        BUTTON_DOWN(KeyEvent.VK_DOWN, (byte)(1 << 5)),
        BUTTON_LEFT(KeyEvent.VK_LEFT, (byte)(1 << 6)),
        BUTTON_RIGHT(KeyEvent.VK_RIGHT, (byte)(1 << 7));

        public int keyCode;
        public boolean isPressedByPlayerOne;
        public boolean isPressedByPlayerTwo;
        public boolean isPressedLocally;
        public byte bit;

        private Button(int keyCode, byte bit) {
            this.keyCode = keyCode;
            this.isPressedByPlayerOne = false;
            this.isPressedByPlayerTwo = false;
            this.isPressedLocally = false;
            this.bit = bit;
        }

        public static Button fromKeyCode(int keyCode) {
            for (Button button : Button.values()) {
                if (keyCode == button.keyCode)
                    return button;
            }
            return null;
        }
    }

    private boolean poll;
    private byte playerOneBuffer;
    private byte playerTwoBuffer;

    public Controller() {
        poll = false;
        playerOneBuffer = 0;
        playerTwoBuffer = 0;
    }

    private void getKeyBytes() {
        playerOneBuffer = 0;
        playerTwoBuffer = 0;
        for (Button button : Button.values()) {
            if (button.isPressedByPlayerOne)
                playerOneBuffer |= button.bit;
            if (button.isPressedByPlayerTwo)
                playerTwoBuffer |= button.bit;
        }
    }

    @Override
    public boolean addressIsMapped(short address) {
        return address == 0x4016 || address == 0x4017;
    }

    @Override
    public byte readByteFromDevice(short address) {
        int bit;
        if (address == 0x4016) {
            bit = playerOneBuffer & 1;
            playerOneBuffer >>>= 1;
            playerOneBuffer |= 0x80;
        } else {
            bit = playerTwoBuffer & 1;
            playerTwoBuffer >>>= 1;
            playerTwoBuffer |= 0x80;
        }
        return (byte)(bit | 0x40);
    }

    @Override
    public void writeByteToDevice(short address, byte value) {
        boolean prevPoll = poll;
        if (address == 0x4016) {
            poll = (value & 1) != 0;
            if (prevPoll && !poll)
                getKeyBytes();
        }
    }

    public void commitButtonStates(boolean isPlayerOne) {
        for (Button button : Button.values()) {
            if (isPlayerOne)
                button.isPressedByPlayerOne = button.isPressedLocally;
            else
                button.isPressedByPlayerTwo = button.isPressedLocally;
        }
    }

    public String getNetplayButtonStatesMessage(boolean isPlayerOne) {
        final Controller.Button[] buttons = Controller.Button.values();
        final char[] characters = new char[buttons.length];
        for (int i = 0; i < characters.length; i++)
            characters[i] = isPlayerOne ?
                    (buttons[i].isPressedByPlayerOne ? '1' : '0') :
                    (buttons[i].isPressedByPlayerTwo ? '1' : '0');
        return new String(characters);
    }

    public void processNetplayButtonStatesMessage(String message, boolean isPlayerOne) {
        final Controller.Button[] buttons = Controller.Button.values();
        for (int i = 0; i < buttons.length; i++) {
            boolean pressed = message.charAt(i) == '1';
            if (isPlayerOne)
                buttons[i].isPressedByPlayerTwo = pressed;
            else
                buttons[i].isPressedByPlayerOne = pressed;
        }
    }
}
