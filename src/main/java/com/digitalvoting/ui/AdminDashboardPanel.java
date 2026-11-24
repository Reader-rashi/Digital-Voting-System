package com.digitalvoting.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.digitalvoting.dao.AdminDao;
import com.digitalvoting.dao.AuditDao;
import com.digitalvoting.dao.ElectionDao;
import com.digitalvoting.dao.VoterDao;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.model.Models.Admin;
import com.digitalvoting.model.Models.Candidate;
import com.digitalvoting.model.Models.Election;
import com.digitalvoting.model.Models.Party;
import com.digitalvoting.model.Models.ResultRow;
import com.digitalvoting.model.Models.Turnout;
import com.digitalvoting.model.Models.Voter;
import com.digitalvoting.model.Role;
import com.digitalvoting.model.Role.Permission;
import com.digitalvoting.util.Security;
import com.digitalvoting.util.ServiceException;

/** Admin console. Each tab is shown only if the signed-in role has the permission. */
public class AdminDashboardPanel extends JPanel {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Navigator nav;
    private final Admin me = Session.admin;
    private final VoterDao voterDao = new VoterDao();
    private final ElectionDao electionDao = new ElectionDao();
    private final AdminDao adminDao = new AdminDao();
    private final AuditDao auditDao = new AuditDao();

    public AdminDashboardPanel(Navigator nav) {
        this.nav = nav;
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        if (me == null) { add(UI.muted("Please sign in as admin."), BorderLayout.CENTER); return; }

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(UI.h1("Admin dashboard"), BorderLayout.WEST);
        top.add(UI.muted(me.fullName() + " (" + me.role() + ")"), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", overview());
        if (me.role().can(Permission.MANAGE_VOTERS)) tabs.addTab("Voters", votersTab());
        if (me.role().can(Permission.MANAGE_CANDIDATES)) tabs.addTab("Candidates & parties", candidatesTab());
        if (me.role().can(Permission.MANAGE_ELECTIONS)) tabs.addTab("Election scheduling", schedulingTab());
        if (me.role().can(Permission.VIEW_REPORTS)) tabs.addTab("Reports", reportsTab());
        if (me.role().can(Permission.MANAGE_ROLES)) tabs.addTab("Roles & permissions", rolesTab());
        add(tabs, BorderLayout.CENTER);
    }

    private <T> void load(java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> ok) {
        Async.run(work, ok, t -> UI.error(this, t));
    }

    private JPanel flow(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : cs) p.add(c);
        return p;
    }

    private JPanel padded() { JPanel p = new JPanel(new BorderLayout(0, 8)); p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); return p; }

    // ---------------------------------------------------------------- overview
    private JPanel overview() {
        JPanel p = padded();
        JTextArea area = new JTextArea("Loading...");
        area.setEditable(false); area.setFont(UI.BASE);
        p.add(area, BorderLayout.CENTER);
        load(() -> {
            StringBuilder sb = new StringBuilder("Voters by status\n");
            voterDao.countByStatus().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
            sb.append("\nElections: ").append(electionDao.listElections().size());
            return sb.toString();
        }, area::setText);
        return p;
    }

    // ---------------------------------------------------------------- voters
    private JPanel votersTab() {
        JPanel p = padded();
        UI.DataTable table = new UI.DataTable("ID", "Name", "Email", "Mobile", "Aadhaar", "Constituency", "Status", "Face");
        JComboBox<String> filter = new JComboBox<>(new String[]{"ALL", "PENDING", "APPROVED", "REJECTED", "BLOCKED"});
        List<Voter> shown = new ArrayList<>();
        Runnable refresh = () -> load(() -> voterDao.list("ALL".equals(filter.getSelectedItem()) ? null : (String) filter.getSelectedItem()), list -> {
            shown.clear(); shown.addAll(list);
            List<Object[]> rows = new ArrayList<>();
            for (Voter v : list) rows.add(new Object[]{v.id(), v.fullName(), v.email(), v.mobile(), "XXXX-XXXX-" + v.aadhaarLast4(), v.constituency(), v.status(), v.faceEnrolled() ? "Yes" : "No"});
            table.setRows(rows);
        });
        java.util.function.Consumer<String> setStatus = status -> {
            int i = table.selected();
            if (i < 0) { UI.info(this, "Select a voter first."); return; }
            Voter v = shown.get(i);
            load(() -> { voterDao.updateStatus(v.id(), status); auditDao.log("admin:" + me.username(), "VOTER_" + status, "voter=" + v.id()); return true; }, ok -> refresh.run());
        };
        filter.addActionListener(e -> refresh.run());
        p.add(flow(new JLabel("Show:"), filter,
                UI.button("Approve", true, () -> setStatus.accept("APPROVED")),
                UI.button("Reject", false, () -> setStatus.accept("REJECTED")),
                UI.button("Block", false, () -> setStatus.accept("BLOCKED")),
                UI.button("Refresh", false, refresh)), BorderLayout.NORTH);
        p.add(table, BorderLayout.CENTER);
        refresh.run();
        return p;
    }

    // ---------------------------------------------------------------- candidates & parties
    private JPanel candidatesTab() {
        JPanel p = new JPanel(new GridLayout(1, 2, 12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // parties
        JPanel left = new JPanel(new BorderLayout(0, 8));
        UI.DataTable partyTable = new UI.DataTable("ID", "Party", "Symbol");
        List<Party> parties = new ArrayList<>();
        JComboBox<Party> partyBox = new JComboBox<>();
        JTextField pName = UI.text(12), pSymbol = UI.text(8);
        Runnable refreshParties = () -> load(electionDao::listParties, list -> {
            parties.clear(); parties.addAll(list);
            List<Object[]> rows = new ArrayList<>();
            partyBox.removeAllItems();
            for (Party x : list) { rows.add(new Object[]{x.id(), x.name(), x.symbol()}); partyBox.addItem(x); }
            partyTable.setRows(rows);
        });
        left.add(UI.h1("Parties"), BorderLayout.NORTH);
        left.add(partyTable, BorderLayout.CENTER);
        left.add(flow(new JLabel("Name"), pName, new JLabel("Symbol"), pSymbol,
                UI.button("Add", true, () -> load(() -> { electionDao.addParty(pName.getText().trim(), pSymbol.getText().trim()); return true; }, ok -> { pName.setText(""); pSymbol.setText(""); refreshParties.run(); })),
                UI.button("Delete selected", false, () -> {
                    int i = partyTable.selected(); if (i < 0) return;
                    load(() -> { electionDao.deleteParty(parties.get(i).id()); return true; }, ok -> refreshParties.run());
                })), BorderLayout.SOUTH);

        // candidates
        JPanel right = new JPanel(new BorderLayout(0, 8));
        UI.DataTable candTable = new UI.DataTable("ID", "Candidate", "Party");
        List<Candidate> cands = new ArrayList<>();
        JComboBox<Election> electionBox = new JComboBox<>();
        JTextField cName = UI.text(12);
        Runnable refreshCands = () -> {
            Election e = (Election) electionBox.getSelectedItem();
            if (e == null) { candTable.setRows(List.of()); return; }
            load(() -> electionDao.listCandidates(e.id()), list -> {
                cands.clear(); cands.addAll(list);
                List<Object[]> rows = new ArrayList<>();
                for (Candidate c : list) rows.add(new Object[]{c.id(), c.name(), c.partyName() == null ? "Independent" : c.partyName()});
                candTable.setRows(rows);
            });
        };
        electionBox.addActionListener(e -> refreshCands.run());
        load(electionDao::listElections, list -> { electionBox.removeAllItems(); list.forEach(electionBox::addItem); });
        right.add(flow(new JLabel("Election"), electionBox), BorderLayout.NORTH);
        right.add(candTable, BorderLayout.CENTER);
        right.add(flow(new JLabel("Name"), cName, partyBox,
                UI.button("Add", true, () -> {
                    Election e = (Election) electionBox.getSelectedItem();
                    if (e == null || cName.getText().isBlank()) { UI.info(this, "Pick an election and enter a name."); return; }
                    Party sel = (Party) partyBox.getSelectedItem();
                    load(() -> { electionDao.addCandidate(e.id(), cName.getText().trim(), sel == null ? null : sel.id()); return true; }, ok -> { cName.setText(""); refreshCands.run(); });
                }),
                UI.button("Delete selected", false, () -> {
                    int i = candTable.selected(); if (i < 0) return;
                    load(() -> { electionDao.deleteCandidate(cands.get(i).id()); return true; }, ok -> refreshCands.run());
                })), BorderLayout.SOUTH);

        p.add(left); p.add(right);
        refreshParties.run();
        return p;
    }

    // ---------------------------------------------------------------- scheduling
    private JPanel schedulingTab() {
        JPanel p = padded();
        UI.DataTable table = new UI.DataTable("ID", "Title", "Starts", "Ends", "Results declared");
        List<Election> shown = new ArrayList<>();
        Runnable refresh = () -> load(electionDao::listElections, list -> {
            shown.clear(); shown.addAll(list);
            List<Object[]> rows = new ArrayList<>();
            for (Election e : list) rows.add(new Object[]{e.id(), e.title(), e.start().format(DT), e.end().format(DT), e.resultsDeclared() ? "Yes" : "No"});
            table.setRows(rows);
        });
        JTextField title = UI.text(16), start = UI.text(12), end = UI.text(12);
        start.setText(LocalDateTime.now().plusDays(1).withMinute(0).format(DT));
        end.setText(LocalDateTime.now().plusDays(2).withMinute(0).format(DT));
        JButton create = UI.button("Create election", true, () -> {
            final LocalDateTime s, e;
            try { s = LocalDateTime.parse(start.getText().trim(), DT); e = LocalDateTime.parse(end.getText().trim(), DT); }
            catch (java.time.format.DateTimeParseException ex) { UI.error(this, new ServiceException("Use the format yyyy-MM-dd HH:mm")); return; }
            if (title.getText().isBlank() || !e.isAfter(s)) { UI.error(this, new ServiceException("Enter a title and an end time after the start time.")); return; }
            load(() -> { int id = electionDao.createElection(title.getText().trim(), s, e); auditDao.log("admin:" + me.username(), "ELECTION_CREATED", "id=" + id); return id; },
                    id -> { title.setText(""); refresh.run(); });
        });
        java.util.function.Consumer<Boolean> declare = flag -> {
            int i = table.selected(); if (i < 0) { UI.info(this, "Select an election first."); return; }
            Election e = shown.get(i);
            if (flag && !e.isFinished(LocalDateTime.now())) { UI.error(this, new ServiceException("Results can only be declared after voting has ended.")); return; }
            load(() -> { electionDao.setResultsDeclared(e.id(), flag); auditDao.log("admin:" + me.username(), flag ? "RESULTS_DECLARED" : "RESULTS_HIDDEN", "election=" + e.id()); return true; }, ok -> refresh.run());
        };
        p.add(flow(new JLabel("Title"), title, new JLabel("Start"), start, new JLabel("End"), end, create), BorderLayout.NORTH);
        p.add(table, BorderLayout.CENTER);
        p.add(flow(UI.button("Declare results", true, () -> declare.accept(true)), UI.button("Hide results", false, () -> declare.accept(false)),
                UI.button("Refresh", false, refresh)), BorderLayout.SOUTH);
        refresh.run();
        return p;
    }

    // ---------------------------------------------------------------- reports
    private JPanel reportsTab() {
        JPanel p = padded();
        JComboBox<Election> electionBox = new JComboBox<>();
        JLabel turnout = UI.muted(" ");
        UI.DataTable results = new UI.DataTable("Candidate", "Party", "Votes");
        UI.DataTable audit = new UI.DataTable("Time", "Actor", "Action", "Detail");
        Runnable refresh = () -> {
            Election e = (Election) electionBox.getSelectedItem();
            if (e != null) load(() -> new Object[]{electionDao.turnout(e.id()), electionDao.results(e.id())}, res -> {
                Turnout t = (Turnout) res[0];
                turnout.setText("Turnout: " + t.voted() + " of " + t.eligible() + " approved voters" + (t.eligible() == 0 ? "" : String.format(" (%.1f%%)", 100.0 * t.voted() / t.eligible())));
                @SuppressWarnings("unchecked") List<ResultRow> rows = (List<ResultRow>) res[1];
                List<Object[]> data = new ArrayList<>();
                for (ResultRow r : rows) data.add(new Object[]{r.candidate(), r.party(), r.votes()});
                results.setRows(data);
            });
            load(() -> auditDao.recent(200), rows -> audit.setRows(new ArrayList<Object[]>(rows)));
        };
        electionBox.addActionListener(e -> refresh.run());
        load(electionDao::listElections, list -> { electionBox.removeAllItems(); list.forEach(electionBox::addItem); refresh.run(); });
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(flow(new JLabel("Election"), electionBox, UI.button("Refresh", false, refresh)));
        top.add(flow(turnout));
        p.add(top, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, results, audit);
        split.setResizeWeight(0.5);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // ---------------------------------------------------------------- roles
    private JPanel rolesTab() {
        JPanel p = padded();
        UI.DataTable matrix = new UI.DataTable("Role", "Manage voters", "Manage candidates", "Manage elections", "View reports", "Manage roles");
        List<Object[]> m = new ArrayList<>();
        for (Role r : Role.values()) {
            Object[] row = new Object[Permission.values().length + 1];
            row[0] = r.name();
            for (Permission pm : Permission.values()) row[pm.ordinal() + 1] = r.can(pm) ? "Yes" : "-";
            m.add(row);
        }
        matrix.setRows(m);
        matrix.setPreferredSize(new Dimension(600, 120));

        UI.DataTable admins = new UI.DataTable("ID", "Username", "Name", "Role");
        List<Admin> shown = new ArrayList<>();
        Runnable refresh = () -> load(adminDao::list, list -> {
            shown.clear(); shown.addAll(list);
            List<Object[]> rows = new ArrayList<>();
            for (Admin a : list) rows.add(new Object[]{a.id(), a.username(), a.fullName(), a.role()});
            admins.setRows(rows);
        });
        JTextField user = UI.text(8), full = UI.text(10);
        JPasswordField pw = UI.password(8);
        JComboBox<Role> role = new JComboBox<>(Role.values());
        JButton add = UI.button("Add admin", true, () -> {
            if (user.getText().isBlank() || pw.getPassword().length < 8) { UI.error(this, new ServiceException("Username and a password of 8+ characters are required.")); return; }
            load(() -> { adminDao.create(user.getText().trim(), full.getText().trim(), Security.hashPassword(new String(pw.getPassword())), (Role) role.getSelectedItem());
                         auditDao.log("admin:" + me.username(), "ADMIN_CREATED", user.getText().trim()); return true; },
                    ok -> { user.setText(""); full.setText(""); pw.setText(""); refresh.run(); });
        });
        JButton change = UI.button("Set selected admin's role", false, () -> {
            int i = admins.selected(); if (i < 0) return;
            Admin a = shown.get(i);
            if (a.id() == me.id()) { UI.error(this, new ServiceException("You cannot change your own role.")); return; }
            load(() -> { adminDao.updateRole(a.id(), (Role) role.getSelectedItem()); auditDao.log("admin:" + me.username(), "ROLE_CHANGED", a.username() + "->" + role.getSelectedItem()); return true; }, ok -> refresh.run());
        });
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(admins, BorderLayout.CENTER);
        center.add(flow(new JLabel("User"), user, new JLabel("Name"), full, new JLabel("Password"), pw, role, add, change), BorderLayout.SOUTH);
        p.add(matrix, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        refresh.run();
        return p;
    }
}