package com.digitalvoting.dao;

import com.digitalvoting.db.Database;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.model.Role;

import java.sql.*;
import java.util.*;

public class AdminDao {
    private Admin map(ResultSet r) throws SQLException {
        return new Admin(r.getInt("admin_id"), r.getString("username"), r.getString("full_name"), Role.valueOf(r.getString("role")));
    }

    public Optional<AdminAuth> findAuth(String username) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("SELECT * FROM admins WHERE username=?")) {
            p.setString(1, username);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(new AdminAuth(map(r), r.getString("password_hash"))) : Optional.empty();
            }
        }
    }

    public void create(String username, String fullName, String passwordHash, Role role) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("INSERT INTO admins (username,full_name,password_hash,role) VALUES (?,?,?,?)")) {
            p.setString(1, username); p.setString(2, fullName); p.setString(3, passwordHash); p.setString(4, role.name());
            p.executeUpdate();
        }
    }

    public List<Admin> list() throws SQLException {
        try (Connection c = Database.get(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM admins ORDER BY admin_id")) {
            List<Admin> out = new ArrayList<>();
            while (r.next()) out.add(map(r));
            return out;
        }
    }

    public void updateRole(int adminId, Role role) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("UPDATE admins SET role=? WHERE admin_id=?")) {
            p.setString(1, role.name()); p.setInt(2, adminId); p.executeUpdate();
        }
    }
}
