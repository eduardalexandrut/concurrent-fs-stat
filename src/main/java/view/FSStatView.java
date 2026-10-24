package view;

import lib.FSReport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

/**
 * Minimal Swing GUI shared by the three versions.
 *
 * The view contains no concurrency logic: it forwards user commands to a
 * ViewListener and exposes thread-safe methods (showReport, showFinished)
 * that can be called from any thread, since they hand the actual update
 * over to the Event Dispatch Thread with invokeLater.
 */
public class FSStatView extends JFrame {

    private final ViewListener listener;
    private final HistogramPanel histogram = new HistogramPanel();
    private final JTextArea statsArea = new JTextArea(14, 32);
    private final JLabel statusLabel = new JLabel("Ready.");

    private final JTextField folderField = new JTextField(System.getProperty("user.home"), 22);
    private final JTextField nbField = new JTextField("5", 4);
    private final JTextField maxSizeField = new JTextField("1000000", 9);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");

    public FSStatView(String title, ViewListener listener) {
        super("FSStat - " + title);
        this.listener = listener;
        setSize(900, 480);
        setLayout(new BorderLayout());

        JPanel inputs = new JPanel(new FlowLayout());
        inputs.add(new JLabel("Folder:"));
        inputs.add(folderField);
        inputs.add(new JLabel("NB:"));
        inputs.add(nbField);
        inputs.add(new JLabel("MaxFS (bytes):"));
        inputs.add(maxSizeField);
        inputs.add(startButton);
        inputs.add(stopButton);
        stopButton.setEnabled(false);
        add(inputs, BorderLayout.NORTH);

        add(histogram, BorderLayout.CENTER);

        statsArea.setEditable(false);
        statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(statsArea), BorderLayout.EAST);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> {
            stopButton.setEnabled(false);
            statusLabel.setText("Stopping...");
            listener.stopRequested();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ev) {
                System.exit(0);
            }
        });
    }

    private void onStart() {
        final int nb;
        final long maxFS;
        try {
            nb = Integer.parseInt(nbField.getText().trim());
            maxFS = Long.parseLong(maxSizeField.getText().trim());
            FSReport.checkParams(maxFS, nb);
        } catch (IllegalArgumentException ex) {   // includes NumberFormatException
            JOptionPane.showMessageDialog(this, "Invalid parameters: " + ex.getMessage());
            return;
        }
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Scanning...");
        showReportNow(FSReport.empty(maxFS, nb));
        listener.startRequested(Path.of(folderField.getText().trim()), maxFS, nb);
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    /** Shows a (partial) report. Can be called from any thread. */
    public void showReport(FSReport report) {
        SwingUtilities.invokeLater(() -> showReportNow(report));
    }

    /** Shows the outcome of the scan and re-enables Start. Can be called from any thread. */
    public void showFinished(FSReport report, String status) {
        SwingUtilities.invokeLater(() -> {
            if (report != null) {
                showReportNow(report);
            }
            statusLabel.setText(status);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    private void showReportNow(FSReport report) {
        histogram.setReport(report);
        statsArea.setText(report.toString());
    }

    /** Histogram of the bands, with labels. */
    private static class HistogramPanel extends JPanel {

        private FSReport report;

        HistogramPanel() {
            setPreferredSize(new Dimension(480, 300));
            setBackground(Color.WHITE);
        }

        void setReport(FSReport report) {
            this.report = report;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (report == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            long[] bands = report.bands();
            int margin = 20;
            int labelArea = 34;
            int barWidth = (getWidth() - 2 * margin) / bands.length;
            int maxHeight = getHeight() - labelArea - 2 * margin;
            long max = 1;
            for (long v : bands) {
                max = Math.max(max, v);
            }
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < bands.length; i++) {
                int h = (int) (bands[i] / (double) max * maxHeight);
                int x = margin + i * barWidth;
                int y = margin + maxHeight - h;
                g2.setColor(i == bands.length - 1 ? new Color(0xC0504D) : new Color(0x4F81BD));
                g2.fillRect(x + 2, y, barWidth - 4, h);
                g2.setColor(Color.DARK_GRAY);
                String count = String.valueOf(bands[i]);
                g2.drawString(count, x + (barWidth - fm.stringWidth(count)) / 2, Math.max(y - 2, fm.getAscent()));
                String label = i == bands.length - 1 ? "> MaxFS" : "B" + i;
                g2.drawString(label, x + (barWidth - fm.stringWidth(label)) / 2, margin + maxHeight + fm.getAscent() + 4);
            }
        }
    }
}
