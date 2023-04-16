package nesemu;

import java.io.IOException;
import java.text.DecimalFormat;
import javax.swing.JLabel;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MainFrame extends javax.swing.JFrame {
    private static final int TARGET_FPS = 60;
    private static final int NANOSECS_PER_FRAME = (int)((1.0 / TARGET_FPS) * 1000000000);
    private NES nes;
    private Thread nesRunnerThread;

    final ScreenPanel panel;
    final javax.swing.JLabel fpsLabel;

    public MainFrame() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("Failed to initialize theme; using fallback");
        }
        nes = null;
        panel = new ScreenPanel();
        fpsLabel = new JLabel("");
        initComponents();
        panel.init();
    }

    class NESRunner implements Runnable {
        private final MainFrame mf;

        public NESRunner(MainFrame mf) {
            this.mf = mf;
        }

        @Override
        public void run() {
            long frames = 0;
            long startTime = System.currentTimeMillis();
            final DecimalFormat df = new DecimalFormat("0.00");
            nes.reset();
            while (!Thread.currentThread().isInterrupted()) {
                long frameStartTime = System.nanoTime(), frameEndTime;
                mf.nes.runUntilFrameReady(mf.panel.img);
                frames++;
                mf.repaint();
                long timeEllapsedMilliseconds = System.currentTimeMillis() - startTime;
                double fps = frames / ((double)timeEllapsedMilliseconds / 1000);
                mf.fpsLabel.setText(df.format(fps));
                do {
                    frameEndTime = System.nanoTime();
                } while (frameEndTime - frameStartTime < NANOSECS_PER_FRAME);
            }
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = panel;
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = fpsLabel;
        menuBar = new javax.swing.JMenuBar();
        jMenu3 = new javax.swing.JMenu();
        loadROMMenuItem = new javax.swing.JMenuItem();
        resetMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        jMenu4 = new javax.swing.JMenu();
        jMenu1 = new javax.swing.JMenu();

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

        jLabel1.setFont(new java.awt.Font("Fira Code Medium", 0, 13)); // NOI18N
        jLabel1.setText("FPS:");

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

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel2)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        if (nes != null)
            nes.controller.keyPressed(evt);
    }//GEN-LAST:event_formKeyPressed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        if (nes != null)
            nes.controller.keyReleased(evt);
    }//GEN-LAST:event_formKeyReleased

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        if (nes != null) {
            int result = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to reset the system?", "Reset",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION)
                nes.reset();
        }
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
        FileNameExtensionFilter filter = new FileNameExtensionFilter("iNES files", "nes");
        fileChooser.setFileFilter(filter);
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            String filePath = fileChooser.getSelectedFile().getAbsolutePath();
            try {
                if (nes == null) {
                    nesRunnerThread = new Thread(new NESRunner(this));
                    nes = new NES(filePath);
                    nesRunnerThread.start();
                } else {
                    nesRunnerThread.interrupt();
                    nes.exchangeCartridge(filePath);
                    nesRunnerThread = new Thread(new NESRunner(this));
                    nesRunnerThread.start();
                }
                requestFocus();
            } catch (IOException ex) {
                Logger.getLogger(this.getClass().getName())
                        .log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_loadROMMenuItemActionPerformed

    public static void main(String args[]) throws IOException {
        MainFrame mf = new MainFrame();
        mf.setLocationRelativeTo(null);
        mf.setVisible(true);

        mf.getContentPane().setBackground(new Color(60, 63, 65));
        mf.fpsLabel.setFont(new java.awt.Font("Fira Code Medium", 0, 13));

        mf.toFront();
        mf.requestFocus();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JMenuItem loadROMMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem resetMenuItem;
    // End of variables declaration//GEN-END:variables
}
