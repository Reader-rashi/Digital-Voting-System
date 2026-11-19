package com.digitalvoting.tools;

import com.digitalvoting.dao.AdminDao;
import com.digitalvoting.model.Role;
import com.digitalvoting.util.Security;

/** Usage: CreateAdmin <username> <password> "<Full Name>" <SUPER_ADMIN|ELECTION_OFFICER|AUDITOR> */
public class CreateAdmin {
    public static void main(String[] a) throws Exception {
        if (a.length != 4) { System.err.println("Usage: CreateAdmin <username> <password> \"<full name>\" <ROLE>"); System.exit(1); }
        new AdminDao().create(a[0], a[2], Security.hashPassword(a[1]), Role.valueOf(a[3]));
        System.out.println("Admin '" + a[0] + "' created with role " + a[3]);
    }
}
