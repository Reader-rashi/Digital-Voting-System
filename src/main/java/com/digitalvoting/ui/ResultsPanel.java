package com.digitalvoting.ui;

import com.digitalvoting.dao.ElectionDao;
import com.digitalvoting.model.Models.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ResultsPanel extends JPanel {
    public ResultsPanel(Navigator nav) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(UI.h1("Election results"), BorderLayout.NORTH);
        JLabel status = UI.muted("Loading...");
        add(status, BorderLayout.CENTER);

        ElectionDao dao = new ElectionDao();
        Async.run(() -> {
            Election e = dao.latestElection().orElse(null);
            if (e == null || !e.resultsDeclared()) return new Object[]{e, null};
            return new Object[]{e, dao.results(e.id())};
        }, res -> {
            Election e = (Election) res[0];
            if (e == null) { status.setText("No election has been created yet."); return; }
            if (res[1] == null) { status.setText("Results not declared yet for \"" + e.title() + "\"."); return; }
            @SuppressWarnings("unchecked") List<ResultRow> rows = (List<ResultRow>) res[1];
            int total = rows.stream().mapToInt(ResultRow::votes).sum();
            UI.DataTable t = new UI.DataTable("Candidate", "Party", "Votes", "Share");
            List<Object[]> data = new ArrayList<>();
            for (ResultRow r : rows) data.add(new Object[]{r.candidate(), r.party(), r.votes(), total == 0 ? "0%" : String.format("%.1f%%", 100.0 * r.votes() / total)});
            t.setRows(data);
            remove(status);
            add(t, BorderLayout.CENTER);
            add(UI.muted(e.title() + " - total votes: " + total), BorderLayout.SOUTH);
            revalidate(); repaint();
        }, t -> status.setText("Could not load results: " + t.getMessage()));
    }
}
