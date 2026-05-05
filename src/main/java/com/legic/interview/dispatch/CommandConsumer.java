package com.legic.interview.dispatch;

import com.rabbitmq.client.*;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.concurrent.*;

public class CommandConsumer {

    private final HikariDataSource ds;

    public CommandConsumer(HikariDataSource ds) {
        this.ds = ds;
    }

    public void consume(Channel channel, Delivery delivery) {
        String json      = new String(delivery.getBody());
        String requestId = extractField(json, "requestId");

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            // Atomic claim: UPDATE wins for exactly one consumer in a cluster
            int claimed = claimCommand(conn, requestId);
            if (claimed == 0) {
                // Already processed or unknown — ack and skip (idempotent drop)
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                conn.rollback();
                return;
            }

            // Actuate physical door — failure here → nack → requeue for retry
            actuateDoor(extractField(json, "doorId"), extractField(json, "op"));

            markDone(conn, requestId);
            conn.commit();
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

        } catch (Exception e) {
            try {
                // nack + requeue; add retry counter + DLQ in production
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            } catch (Exception ignored) {}
        }
    }

    private int claimCommand(Connection conn, String requestId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE commands SET status='PROCESSING' WHERE request_id=? AND status='SENT'")) {
            ps.setString(1, requestId);
            return ps.executeUpdate();
        }
    }

    private void markDone(Connection conn, String requestId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE commands SET status='DONE' WHERE request_id=?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        }
    }

    private void actuateDoor(String doorId, String op) { /* call controller API */ }

    private String extractField(String json, String field) {
        int s = json.indexOf("\"" + field + "\":\"") + field.length() + 4;
        return json.substring(s, json.indexOf("\"", s));
    }
}
