
package com.digitalvoting.ui;

import com.digitalvoting.dao.ElectionDao;
import com.digitalvoting.model.Models.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

public class ResultsPanel extends JPanel {

    private final ElectionDao dao = new ElectionDao();

    public ResultsPanel(Navigator nav) {

        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);

        setBorder(BorderFactory.createEmptyBorder(
                24, 32, 24, 32
        ));

        add(UI.h1("Election Results"), BorderLayout.NORTH);

        JLabel status = UI.muted("Loading election results...");
        add(status, BorderLayout.CENTER);

        loadResults(status);
    }

    private void loadResults(JLabel status) {

        Async.run(() -> {

            Election election =
                    dao.latestElection().orElse(null);

            if (election == null || !election.resultsDeclared()) {
                return new Object[]{election, null};
            }

            List<ResultRow> rows =
                    dao.results(election.id());

            return new Object[]{election, rows};

        }, result -> {

            Election election = (Election) result[0];

            if (election == null) {
                status.setText("No election has been created yet.");
                return;
            }

            if (result[1] == null) {
                status.setText(
                        "Results not declared yet for \""
                                + election.title() + "\"."
                );
                return;
            }

            @SuppressWarnings("unchecked")
            List<ResultRow> rows =
                    (List<ResultRow>) result[1];

            if (rows.isEmpty()) {
                status.setText("No candidate results available.");
                return;
            }

            displayResults(election, rows, status);

        }, error -> {

            status.setText(
                    "Could not load results: "
                            + error.getMessage()
            );

        });
    }

    private void displayResults(
            Election election,
            List<ResultRow> rows,
            JLabel status
    ) {

        int totalVotes = rows.stream()
                .mapToInt(ResultRow::votes)
                .sum();

        // Build chart dataset from actual database results.
        DefaultCategoryDataset dataset =
                new DefaultCategoryDataset();

        for (ResultRow row : rows) {

            dataset.addValue(
                    row.votes(),
                    "Votes",
                    row.candidate()
            );
        }

        // Create vertical bar graph.
        JFreeChart chart = ChartFactory.createBarChart(
                "Candidate-wise Election Results",
                "Candidates",
                "Number of Votes",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(220, 220, 220));

        BarRenderer renderer =
                (BarRenderer) plot.getRenderer();

        renderer.setSeriesPaint(
                0,
                new Color(41, 98, 180)
        );

        renderer.setDrawBarOutline(false);

        // Display vote count above every bar.
        renderer.setDefaultItemLabelsVisible(true);

        renderer.setDefaultItemLabelGenerator(
                new StandardCategoryItemLabelGenerator()
        );

        renderer.setDefaultItemLabelFont(
                new Font("SansSerif", Font.BOLD, 12)
        );

        // Chart panel.
        ChartPanel chartPanel = new ChartPanel(chart);

        chartPanel.setPreferredSize(
                new Dimension(850, 400)
        );

        chartPanel.setMouseWheelEnabled(true);

        // Results table.
        UI.DataTable table = new UI.DataTable(
                "Candidate",
                "Party",
                "Votes",
                "Share"
        );

        List<Object[]> data = new ArrayList<>();

        for (ResultRow row : rows) {

            String share = totalVotes == 0
                    ? "0%"
                    : String.format(
                            "%.1f%%",
                            100.0 * row.votes() / totalVotes
                    );

            data.add(new Object[]{
                    row.candidate(),
                    row.party(),
                    row.votes(),
                    share
            });
        }

        table.setRows(data);

        // Combine chart and table.
        JPanel content = new JPanel(new BorderLayout(0, 15));

        content.setBackground(UI.BG);

        content.add(chartPanel, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(content);

        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        remove(status);

        add(scrollPane, BorderLayout.CENTER);

        add(
                UI.muted(
                        election.title()
                                + " | Total votes: "
                                + totalVotes
                ),
                BorderLayout.SOUTH
        );

        revalidate();
        repaint();
    }
}
