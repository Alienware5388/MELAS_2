package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class StressStrainPlotter {
    private JFrame frame;
    private JTextArea inputArea;
    private JTextArea apdlOutput;
    private JPanel chartPanel;
    private JComboBox<String> meshType;
    private JTextField temperatureField;
    private JTextField poissonField;
    private JTextField materialField;
    private JButton generateAPDLButton;
    private JButton switchInputButton;
    private boolean isStressStrain = true;

    public StressStrainPlotter() {
        frame = new JFrame("Stress-Strain Curve Plotter");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Create control panel for user inputs
        JPanel controlPanel = new JPanel(new GridLayout(6, 2));

        meshType = new JComboBox<>(new String[]{"Tetrahedrons", "Hexahedrons"});
        temperatureField = new JTextField("22");
        poissonField = new JTextField("0.35");
        materialField = new JTextField("Material Name");
        JButton plotButton = new JButton("Plot Curve");
        generateAPDLButton = new JButton("Generate APDL");
        switchInputButton = new JButton("Switch Input Order");

        plotButton.addActionListener(e -> plotGraph());
        generateAPDLButton.addActionListener(e -> generateAPDL());
        switchInputButton.addActionListener(e -> switchInputOrder());

        controlPanel.add(new JLabel("Material Name:"));
        controlPanel.add(materialField);
        controlPanel.add(new JLabel("Mesh Type:"));
        controlPanel.add(meshType);
        controlPanel.add(new JLabel("Temperature [°C]:"));
        controlPanel.add(temperatureField);
        controlPanel.add(new JLabel("Poisson Ratio:"));
        controlPanel.add(poissonField);

        //JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        controlPanel.add(plotButton);
        controlPanel.add(generateAPDLButton);
        controlPanel.add(switchInputButton);

        frame.add(controlPanel, BorderLayout.NORTH);
        //frame.add(buttonPanel, BorderLayout.WEST);

        // Input and output areas
        inputArea = new JTextArea(10, 20);
        apdlOutput = new JTextArea(10, 20);
        apdlOutput.setEditable(false);

        JPanel textPanel = new JPanel(new GridLayout(1, 2));
        textPanel.add(new JScrollPane(inputArea));
        textPanel.add(new JScrollPane(apdlOutput));
        frame.add(textPanel, BorderLayout.CENTER);

        // Panel for graph visualization
        chartPanel = new JPanel(new BorderLayout());
        frame.add(chartPanel, BorderLayout.EAST);

        frame.setVisible(true);
    }

    private void switchInputOrder() {
        try {
            String[] lines = inputArea.getText().split("\n");
            StringBuilder switchedData = new StringBuilder();
            for (String line : lines) {
                String[] values = line.trim().split("\\s+");
                if (values.length == 2) {
                    switchedData.append(values[1]).append("\t").append(values[0]).append("\n");
                }
            }
            inputArea.setText(switchedData.toString());
            isStressStrain = !isStressStrain;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error switching input order: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void plotGraph() {
        try {
            XYSeries series = new XYSeries(materialField.getText());
            String[] lines = inputArea.getText().split("\n");
            for (String line : lines) {
                String[] values = line.trim().split("\\s+");
                if (values.length == 2) {
                    double x = Double.parseDouble(values[0]) / 100.0;
                    double y = Double.parseDouble(values[1]);
                    series.add(x, y);
                } else {
                    throw new NumberFormatException("Invalid data format.");
                }
            }

            XYSeriesCollection dataset = new XYSeriesCollection(series);
            JFreeChart chart = ChartFactory.createXYLineChart(
                    materialField.getText()+" --- Stress-Strain Curve", "Strain [-]", "Stress [MPa]",
                    dataset
            );

            chartPanel.removeAll();
            chartPanel.add(new ChartPanel(chart), BorderLayout.CENTER);
            chartPanel.revalidate();
            chartPanel.repaint();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error plotting graph: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateAPDL() {
        try {
            StringBuilder apdl = new StringBuilder("! Commands inserted into this file will be executed just after material definitions in /PREP7.\n" +
                    "\n" +
                    "! Active UNIT system in Workbench when this object was created: Metric (mm, t, N, s, mV, mA)\n" +
                    "\n" +
                    "! NOTE: Any data that requires units (such as mass) is assumed to be in the consistent solver unit system.\n" +
                    "\n" +
                    "! See Solving Units in the help system for more information.");

            apdl.append("/prep7\n");
            apdl.append("MPTEMP,,,,,,,,\n");
            apdl.append("MPTEMP, 1, ").append(temperatureField.getText()).append("\n");

            String[] lines = inputArea.getText().split("\n");
            if (lines.length == 0) throw new NumberFormatException("Empty dataset.");

            String[] firstValues = lines[0].trim().split("\\s+");
            double firstStrain = Double.parseDouble(firstValues[0]) / 100.0;
            double firstStress = Double.parseDouble(firstValues[1]);
            double youngsModulus = firstStress / firstStrain;
            apdl.append("MPDATA, EX, MATID, , ").append(youngsModulus).append("\n");
            apdl.append("MPDATA, PRXY, MATID, , ").append(poissonField.getText()).append("\n");

            String mesh = meshType.getSelectedItem().toString();
            String elementType = mesh.equals("Tetrahedrons") ? "SOLID92" : "SOLID95";
            apdl.append("ET, MATID, ").append(elementType).append("\n");

            apdl.append("TB, MELA, MATID, 1, ").append(lines.length).append("\n");
            apdl.append("TBTEMP, ").append(temperatureField.getText()).append("\n");

            for (String line : lines) {
                String[] values = line.trim().split("\\s+");
                double strain = Double.parseDouble(values[0]) / 100.0;
                double stress = Double.parseDouble(values[1]);
                apdl.append("TBPT,,").append(String.format("%.4f", strain)).append(", ").append(stress).append("\n");
            }

            apdlOutput.setText(apdl.toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error generating APDL: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new StressStrainPlotter();
        });

    }
}



