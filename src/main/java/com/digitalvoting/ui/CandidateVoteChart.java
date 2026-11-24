
package com.digitalvoting.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

public class CandidateVoteChart extends JPanel {

    public CandidateVoteChart(Map<String, Integer> results) {

        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        DefaultCategoryDataset dataset =
                new DefaultCategoryDataset();

        for (Map.Entry<String, Integer> entry : results.entrySet()) {

            dataset.addValue(
                    entry.getValue(),
                    "Votes",
                    entry.getKey()
            );
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Election Results",
                "Candidates",
                "Number of Votes",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        BarRenderer renderer =
                (BarRenderer) plot.getRenderer();

        renderer.setSeriesPaint(
                0,
                new Color(41, 98, 180)
        );

        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.12);

        ChartPanel chartPanel = new ChartPanel(chart);

        chartPanel.setMouseWheelEnabled(true);

        add(chartPanel, BorderLayout.CENTER);
    }
}
