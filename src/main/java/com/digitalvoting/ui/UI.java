package com.digitalvoting.ui;

import com.digitalvoting.util.ServiceException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Shared look & feel helpers. */
public final class UI {
    public static final Color NAVY = new Color(0x1B2A49), ACCENT = new Color(0xE8741E), BG = new Color(0xF5F4EF),
            GREEN = new Color(0x1E8E3E), CARD = Color.WHITE, MUTED = new Color(0x6B7280);
    public static final Font BASE = new Font("SansSerif", Font.PLAIN, 14);

    private UI() {}

    public static JLabel h1(String t) { JLabel l = new JLabel(t); l.setFont(BASE.deriveFont(Font.BOLD, 24f)); l.setForeground(NAVY); return l; }
    public static JLabel muted(String t) { JLabel l = new JLabel(t); l.setFont(BASE); l.setForeground(MUTED); return l; }

    public static JButton button(String text, boolean primary, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(BASE.deriveFont(Font.BOLD));
        b.setFocusPainted(false);
        if (primary) { b.setBackground(ACCENT); b.setForeground(Color.WHITE); b.setOpaque(true); b.setBorderPainted(false); }
        b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JPanel page() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        return p;
    }

    /** Label/field form using GridBagLayout. */
    public static class Form extends JPanel {
        private int row = 0;
        public Form() { super(new GridBagLayout()); setOpaque(false); }
        public <C extends JComponent> C row(String label, C field) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++; g.insets = new Insets(6, 0, 6, 12); g.anchor = GridBagConstraints.WEST;
            g.gridx = 0; add(new JLabel(label), g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; add(field, g);
            return field;
        }
        public void addWide(JComponent c) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++; g.gridx = 0; g.gridwidth = 2; g.insets = new Insets(10, 0, 0, 0); g.anchor = GridBagConstraints.WEST;
            add(c, g);
        }
    }

    public static JTextField text(int cols) { JTextField f = new JTextField(cols); f.setFont(BASE); return f; }
    public static JPasswordField password(int cols) { JPasswordField f = new JPasswordField(cols); f.setFont(BASE); return f; }

    public static void info(Component parent, String msg) { JOptionPane.showMessageDialog(parent, msg, "Digital Voting System", JOptionPane.INFORMATION_MESSAGE); }

    public static void error(Component parent, Throwable t) {
        String msg = (t instanceof ServiceException || t instanceof IllegalArgumentException || t instanceof java.time.format.DateTimeParseException)
                ? t.getMessage() : "Unexpected error: " + t;
        if (!(t instanceof ServiceException)) t.printStackTrace();
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Read-only table with simple row replacement. */
    public static class DataTable extends JScrollPane {
        private final DefaultTableModel model;
        private final JTable table;
        public DataTable(String... cols) {
            model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
            table = new JTable(model);
            table.setRowHeight(26);
            table.setFont(BASE);
            table.setAutoCreateRowSorter(false);
            setViewportView(table);
        }
        public void setRows(List<Object[]> rows) { model.setRowCount(0); rows.forEach(model::addRow); }
        /** Index into the list given to setRows, or -1. */
        public int selected() { return table.getSelectedRow(); }
    }
}
