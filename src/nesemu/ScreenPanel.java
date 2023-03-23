package nesemu;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class ScreenPanel extends javax.swing.JPanel {
    BufferedImage img;

    public ScreenPanel() {
        initComponents();
    }

    @Override
    public void paint(Graphics g) {
        g.drawImage(img, 0, 0, 2 * img.getWidth(), 2 * img.getHeight(), null);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    void init() {
        final int width = getWidth();
        final int height = getHeight();
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
