package com.legic.interview.dispatch;

import com.rabbitmq.client.*;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.concurrent.*;

public class CommandDispatcher {

    private final HikariDataSource ds;
    private final Channel          rabbitChannel;

    public CommandDispatcher(HikariDataSource ds, Channel rabbitChannel) {
        this.ds            = ds;
        this.rabbitChannel = rabbitChannel;
    }

    public DispatchResult dispatch(Command cmd) {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Atomic upsert — ON CONFLICT DO NOTHING is the idempotency guard
            String insert = """
                INSERT INTO commands(request_id, door_id, operation, status)
                VALUES (?, ?, ?, 'PENDING')
                ON CONFLICT (request_id) DO NOTHING
                """;
            int inserted;
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, cmd.requestId());
                ps.setString(2, cmd.doorId());
                ps.setString(3, cmd.op());
                inserted = ps.executeUpdate();
            }

            if (inserted == 0) {
                // Duplicate: return current status to caller — no side effects
                conn.rollback();
                String status = fetchStatus(conn, cmd.requestId());
                return new DispatchResult.Duplicate(cmd.requestId(), status);
            }

            // Step 2: Publish with confirm BEFORE committing DB
            // If Rabbit is down → exception → rollback → command stays PENDING
            publishCommand(cmd);

            // Step 3: Mark SENT — optimistic lock guards against concurrent retry
            int updated = markSent(conn, cmd.requestId(), 0); // version=0 for fresh row
            if (updated == 0) {
                conn.rollback();
                return new DispatchResult.Failed(cmd.requestId(), "Concurrent update conflict");
            }
            conn.commit();
            return new DispatchResult.Sent(cmd.requestId());

        } catch (Exception e) {
            return new DispatchResult.Failed(cmd.requestId(), e.getMessage());
        }
    }

    private String fetchStatus(Connection conn, String requestId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM commands WHERE request_id=?")) {
            ps.setString(1, requestId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("status") : "UNKNOWN";
        }
    }

    private int markSent(Connection conn, String requestId, int expectedVersion)
            throws SQLException {
        String sql = """
            UPDATE commands
               SET status='SENT', version = version + 1
             WHERE request_id = ?
               AND status     = 'PENDING'
               AND version    = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setInt(2, expectedVersion);
            return ps.executeUpdate();
        }
    }

    private void publishCommand(Command cmd) throws Exception {
        byte[] body = ("{\"requestId\":\"" + cmd.requestId() +
                       "\",\"doorId\":\""  + cmd.doorId()    +
                       "\",\"op\":\""      + cmd.op()        + "\"}").getBytes();
        synchronized (rabbitChannel) {
            rabbitChannel.basicPublish("commands", "door." + cmd.doorId(),
                    MessageProperties.PERSISTENT_TEXT_PLAIN, body);
            rabbitChannel.waitForConfirms(3_000); // blocks virtual thread — safe
        }
    }
}

// ── Idempotent consumer ───────────────────────────────────────────────────────
