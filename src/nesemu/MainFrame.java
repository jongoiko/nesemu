package nesemu;

import java.io.IOException;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MainFrame extends javax.swing.JFrame {
    private static final int TARGET_FPS = 60;
    private static final int NANOSECS_PER_FRAME = (int)((1.0 / TARGET_FPS) * 1000000000);
    private static final int NETPLAY_PORT = 6502;

    private NES nes;
    private NESRunnerThread nesRunnerThread;

    private Socket netplaySocket;
    private boolean isNetplayServer;
    private NetplayServerThread netplayServerThread;

    final ScreenPanel panel;

    public MainFrame() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("Failed to initialize theme; using fallback");
        }
        nes = null;
        panel = new ScreenPanel();
        initComponents();
        panel.init();
    }

    private class NESRunnerThread extends Thread {
        private static final AtomicBoolean shouldPerformNetplaySync =
                new AtomicBoolean(false);
        private static final AtomicBoolean shouldReset = new AtomicBoolean(false);

        @Override
        public synchronized void run() {
            if (netplaySocket == null || isNetplayServer)
                nes.reset();
            final boolean isPlayerOne = netplaySocket == null || isNetplayServer;
            boolean sendResetMessage = false;
            while (!Thread.currentThread().isInterrupted()) {
                if (shouldPerformNetplaySync.compareAndSet(true, false))
                    performInitialNetplaySync();
                if (shouldReset.compareAndSet(true, false)) {
                    nes.reset();
                    sendResetMessage = true;
                }
                long frameStartTime = System.nanoTime(), frameEndTime;
                nes.controller.commitButtonStates(isPlayerOne);
                if (netplaySocket != null) {
                    try {
                        if (sendResetMessage && isNetplayServer) {
                            netplaySendResetMessage();
                            sendResetMessage = false;
                        } else
                            netplaySendButtonStates(isPlayerOne);
                        netplayReceiveMessage(isPlayerOne);
                    } catch (IOException ex) {
                        Logger.getLogger(MainFrame.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                }
                nes.runUntilFrameReady(panel.img);
                repaint();
                do {
                    frameEndTime = System.nanoTime();
                } while (frameEndTime - frameStartTime < NANOSECS_PER_FRAME);
            }
        }

        private void performInitialNetplaySync() {
            try {
                if (isNetplayServer) {
                    netplaySocket.getOutputStream().write(10);
                    (new ObjectOutputStream(netplaySocket.getOutputStream()))
                            .writeObject(nes);
                    while (netplaySocket.getInputStream().read() == -1) {

                    }
                } else {
                    while (netplaySocket.getInputStream().read() == -1) {

                    }
                    nes = (NES)(new ObjectInputStream(netplaySocket.getInputStream()))
                            .readObject();
                    nes.runUntilFrameReady(panel.img);
                    netplaySocket.getOutputStream().write(10);
                }
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(this.getClass().getName())
                        .log(Level.SEVERE, null, ex);
            }
        }

        public static void requestInitialNetplaySync() {
            shouldPerformNetplaySync.set(true);
        }

        public static void requestReset() {
            shouldReset.set(true);
        }

        private void netplaySendResetMessage() throws IOException {
            DataOutputStream out =
                    new DataOutputStream(netplaySocket.getOutputStream());
            out.writeUTF("RESET");
            out.flush();
        }

        private void netplaySendButtonStates(boolean isPlayerOne) throws IOException {
            DataOutputStream out =
                    new DataOutputStream(netplaySocket.getOutputStream());
            out.writeUTF("BUTTONS " + nes.controller.getNetplayButtonStatesMessage(isPlayerOne));
            out.flush();
        }

        private void netplayReceiveMessage(boolean isPlayerOne) throws IOException {
            try {
                final DataInputStream in =
                        new DataInputStream(netplaySocket.getInputStream());
                String words[] = in.readUTF().split(" ");
                if (words[0].equals("RESET"))
                    NESRunnerThread.requestReset();
                else
                    nes.controller.processNetplayButtonStatesMessage(words[1], isPlayerOne);
            } catch (IOException ex) {
                netplaySocket.close();
                statusBarLabel.setText("Connection closed by " +
                        (isNetplayServer ? "client" : "server"));
                netplaySocket = null;
                loadROMMenuItem.setEnabled(true);
                resetMenuItem.setEnabled(true);
            }
        }
    }

    private class NetplayServerThread extends Thread {
        @Override
        public void run() {
            statusBarLabel.setText("Netplay server started; waiting for connections");
            final ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(NETPLAY_PORT);
                Socket clientSocket = serverSocket.accept();
                statusBarLabel.setText("Accepted connection from "
                    + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                netplaySocket = clientSocket;
                isNetplayServer = true;
                NESRunnerThread.requestInitialNetplaySync();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Could not start netplay server " +
                        "(" + ex.getLocalizedMessage() + ").", "Netplay server error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = panel;
        statusBarLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        jMenu3 = new javax.swing.JMenu();
        loadROMMenuItem = new javax.swing.JMenuItem();
        resetMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        jMenu4 = new javax.swing.JMenu();
        jMenu1 = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();
        jMenu5 = new javax.swing.JMenu();
        startServerMenuItem = new javax.swing.JMenuItem();
        stopServerMenuItem = new javax.swing.JMenuItem();
        connectToServerMenuItem = new javax.swing.JMenuItem();
        disconnectFromServerMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setAutoRequestFocus(false);
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setFont(new java.awt.Font("Fira Code SemiBold", 0, 10)); // NOI18N
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 512, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 480, Short.MAX_VALUE)
        );

        statusBarLabel.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        statusBarLabel.setText("Load a ROM to play with System -> Load ROM.");

        menuBar.setFont(new java.awt.Font("Fira Code Medium", 0, 13)); // NOI18N

        jMenu3.setText("System");

        loadROMMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        loadROMMenuItem.setText("Load ROM");
        loadROMMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadROMMenuItemActionPerformed(evt);
            }
        });
        jMenu3.add(loadROMMenuItem);

        resetMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        resetMenuItem.setText("Reset");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        jMenu3.add(resetMenuItem);

        exitMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        jMenu3.add(exitMenuItem);

        menuBar.add(jMenu3);

        jMenu4.setText("Settings");
        menuBar.add(jMenu4);

        jMenu1.setText("Help");
        menuBar.add(jMenu1);

        jMenu2.setText("Netplay");

        jMenu5.setText("Local server");
        jMenu5.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N

        startServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        startServerMenuItem.setText("Start");
        startServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startServerMenuItemActionPerformed(evt);
            }
        });
        jMenu5.add(startServerMenuItem);

        stopServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        stopServerMenuItem.setText("Stop");
        stopServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopServerMenuItemActionPerformed(evt);
            }
        });
        jMenu5.add(stopServerMenuItem);

        jMenu2.add(jMenu5);

        connectToServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        connectToServerMenuItem.setText("Connect to server");
        connectToServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectToServerMenuItemActionPerformed(evt);
            }
        });
        jMenu2.add(connectToServerMenuItem);

        disconnectFromServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        disconnectFromServerMenuItem.setText("Disconnect from server");
        disconnectFromServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                disconnectFromServerMenuItemActionPerformed(evt);
            }
        });
        jMenu2.add(disconnectFromServerMenuItem);

        menuBar.add(jMenu2);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(statusBarLabel))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusBarLabel)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        Controller.Button button;
        if (nes == null || (button =
                Controller.Button.fromKeyCode(evt.getKeyCode())) == null)
            return;
        button.isPressedLocally = true;
    }//GEN-LAST:event_formKeyPressed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        Controller.Button button;
        if (nes == null || (button =
                Controller.Button.fromKeyCode(evt.getKeyCode())) == null)
            return;
        button.isPressedLocally = false;
    }//GEN-LAST:event_formKeyReleased

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        if (nes == null)
            return;
        int result = JOptionPane.showConfirmDialog(null,
                "Are you sure you want to reset the system?", "Reset",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION)
            NESRunnerThread.requestReset();
    }//GEN-LAST:event_resetMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        int result = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to exit?", "Exit",
                    JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION)
            System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void loadROMMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadROMMenuItemActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "iNES cartridge files (.nes)", "nes");
        fileChooser.setFileFilter(filter);
        int result = fileChooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION)
            return;
        String fileName = fileChooser.getSelectedFile().getName();
        String filePath = fileChooser.getSelectedFile().getAbsolutePath();
        try {
            if (nes == null) {
                nesRunnerThread = new NESRunnerThread();
                nes = new NES(filePath);
                nesRunnerThread.start();
            } else {
                nesRunnerThread.interrupt();
                nes.exchangeCartridge(filePath);
                nesRunnerThread = new NESRunnerThread();
                nesRunnerThread.start();
            }
            statusBarLabel.setText("");
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName())
                    .log(Level.SEVERE, null, ex);
        } catch (UnsupportedMapperException ex) {
            JOptionPane.showMessageDialog(null,
                    "The mapper associated to \"" + fileName + "\" (iNES mapper number " +
                    ex.getMapperNumber() + ") is not supported yet.",
                    "Unsupported mapper", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(null, "\"" + fileName +
                    "\" is not a valid cartridge file.", "Invalid cartridge file",
                    JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_loadROMMenuItemActionPerformed

    private void startServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startServerMenuItemActionPerformed
        netplayServerThread = new NetplayServerThread();
        netplayServerThread.start();
    }//GEN-LAST:event_startServerMenuItemActionPerformed

    private void stopServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopServerMenuItemActionPerformed
        if (netplayServerThread != null && netplayServerThread.isAlive()) {
            netplayServerThread.interrupt();
            statusBarLabel.setText("Netplay server stopped");
            if (netplaySocket == null)
                return;
            try {
                netplaySocket.close();
            } catch (IOException ex) {
                Logger.getLogger(MainFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }
        netplaySocket = null;
    }//GEN-LAST:event_stopServerMenuItemActionPerformed

    private void connectToServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectToServerMenuItemActionPerformed
        String host = (String)JOptionPane.showInputDialog(null,
                "Input the server's hostname or IP address:",
                "Netplay server address", JOptionPane.QUESTION_MESSAGE,
                null, null, "localhost");
        if (host == null)
            return;
        if (netplayServerThread != null && netplayServerThread.isAlive())
            netplayServerThread.interrupt();
        try {
            netplaySocket = new Socket(host, NETPLAY_PORT);
            statusBarLabel.setText("Successfully connected to server");
            loadROMMenuItem.setEnabled(false);
            resetMenuItem.setEnabled(false);
            isNetplayServer = false;
            if (nes != null)
                nesRunnerThread.interrupt();
            NESRunnerThread.requestInitialNetplaySync();
            nesRunnerThread = new NESRunnerThread();
            nesRunnerThread.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Could not connect to server " +
                    "(" + ex.getLocalizedMessage() + ").", "Connection error",
                        JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_connectToServerMenuItemActionPerformed

    private void disconnectFromServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disconnectFromServerMenuItemActionPerformed
        if (netplaySocket == null || isNetplayServer)
            return;
        try {
            netplaySocket.close();
            statusBarLabel.setText("Disconnected from server");
            loadROMMenuItem.setEnabled(true);
            resetMenuItem.setEnabled(true);
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_disconnectFromServerMenuItemActionPerformed

    public static void main(String args[]) throws IOException {
        MainFrame mf = new MainFrame();
        mf.setLocationRelativeTo(null);
        mf.setVisible(true);

        mf.getContentPane().setBackground(new Color(60, 63, 65));

        mf.toFront();
        mf.requestFocus();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem connectToServerMenuItem;
    private javax.swing.JMenuItem disconnectFromServerMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JMenu jMenu5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JMenuItem loadROMMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JMenuItem startServerMenuItem;
    private javax.swing.JLabel statusBarLabel;
    private javax.swing.JMenuItem stopServerMenuItem;
    // End of variables declaration//GEN-END:variables
}
