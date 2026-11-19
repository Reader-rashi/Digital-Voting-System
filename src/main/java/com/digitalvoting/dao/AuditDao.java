package com.digitalvoting.dao;

import com.digitalvoting.db.Database;

import java.sql.*;
import java.util.*;

public class AuditDao {
    /** Best-effort: audit failures must never break the main action. */
    public void log(String actor, String action, String detail) {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("INSERT INTO audit_log (actor,action,detail) VALUES (?,?,?)")) {
            p.setString(1, actor); p.setString(2, action); p.setString(3, detail); p.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public List<String[]> recent(int limit) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("SELECT logged_at,actor,action,detail FROM audit_log ORDER BY id DESC LIMIT ?")) {
            p.setInt(1, limit);
            try (ResultSet r = p.executeQuery()) {
                List<String[]> out = new ArrayList<>();
                while (r.next()) out.add(new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4)});
                return out;
            }
        }
    }
}
