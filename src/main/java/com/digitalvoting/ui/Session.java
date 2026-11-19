package com.digitalvoting.ui;

import com.digitalvoting.api.FaceService;
import com.digitalvoting.model.Models.*;

/** Who is logged in right now (single-user desktop app). */
public final class Session {
    private Session() {}
    public static Voter voter;
    public static Admin admin;
    public static VoteReceipt receipt;
    public static final FaceService FACE = new FaceService();

    public static void clear() { voter = null; admin = null; receipt = null; }
}
