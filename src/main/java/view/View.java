package view;

import controller.Controller;
import lib.FSReport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;

import java.awt.event.WindowEvent;

public class View extends JFrame {

    private Timer refreshTimer;

    public void startPolling() {
        refreshTimer = new Timer(200, e -> {
            render(controller.getCurrentReport()); // pull every 200ms
        });
        refreshTimer.start();
    }

    public void stopPolling() {
        if (refreshTimer != null) refreshTimer.stop();
    }


    private final Controller controller;
    private final VisualiserPanel panel;

    private JTextField folderField;
    private JTextField nbField;
    private JTextField maxSizeField;

    private JButton startButton;
    private JButton stopButton;

    private JTextArea statsArea;

    private final int WIDTH = 600;
    private final int HEIGHT = 400;

    public View(Controller controller){
        this.controller = controller;

        setTitle("FSStatLib");
        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setLayout(new BorderLayout());

        // ===== TOP CONTROL PANEL =====
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridLayout(2,1));

        JPanel inputs = new JPanel();
        inputs.setLayout(new FlowLayout());

        folderField = new JTextField(20);
        nbField = new JTextField("5",5);
        maxSizeField = new JTextField("1000000",8);

        inputs.add(new JLabel("Folder:"));
        inputs.add(folderField);

        inputs.add(new JLabel("NB:"));
        inputs.add(nbField);

        inputs.add(new JLabel("MaxFileSize:"));
        inputs.add(maxSizeField);

        topPanel.add(inputs);

        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());

        startButton = new JButton("Start");
        stopButton = new JButton("Stop");

        buttons.add(startButton);
        buttons.add(stopButton);

        topPanel.add(buttons);

        add(topPanel, BorderLayout.NORTH);

        // ===== CENTER HISTOGRAM PANEL =====
        panel = new VisualiserPanel(400,250);
        add(panel, BorderLayout.CENTER);

        // ===== RIGHT STATS PANEL =====
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setPreferredSize(new Dimension(180, HEIGHT));

        JScrollPane scroll = new JScrollPane(statsArea);
        add(scroll, BorderLayout.EAST);

        // ===== BUTTON ACTIONS =====
        startButton.addActionListener(e -> {
            controller.start(
                    folderField.getText(),
                    Integer.parseInt(nbField.getText()),
                    Long.parseLong(maxSizeField.getText())
            );
        });

        stopButton.addActionListener(e -> controller.stop());

        addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent ev){
                System.exit(0);
            }
        });

        setVisible(true);
    }

    // ===== UPDATE VIEW =====
    public void render(FSReport report){

        panel.setBands(report.bands());

        StringBuilder sb = new StringBuilder();
        sb.append("Total files: ").append(report.totalFiles()).append("\n\n");

        for (int i = 0; i < report.bands().length; i++){
            sb.append("Band ").append(i).append(": ")
                    .append(report.bands()[i]).append("\n");
        }

        statsArea.setText(sb.toString());
    }

    // =========================================================
    // ================= VISUALIZER PANEL ======================
    // =========================================================

    public class VisualiserPanel extends JPanel {

        private long[] bands = new long[0];

        public VisualiserPanel(int w, int h){
            setPreferredSize(new Dimension(w,h));
        }

        public void setBands(long[] bands){
            this.bands = bands;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (bands.length == 0) return;

            int width = getWidth();
            int height = getHeight();

            int barWidth = width / bands.length;

            long max = 1;
            for (long v : bands)
                if (v > max) max = v;

            for (int i = 0; i < bands.length; i++) {

                int barHeight = (int)((bands[i] / (double)max) * (height - 20));

                int x = i * barWidth;
                int y = height - barHeight;

                g2.setColor(Color.BLUE);
                g2.fillRect(x, y, barWidth - 2, barHeight);

                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, barWidth - 2, barHeight);
            }
        }
    }
}