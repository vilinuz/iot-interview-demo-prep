package com.legic.interview.dispatch;

import com.rabbitmq.client.*;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.concurrent.*;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. ON CONFLICT DO NOTHING: atomic insert guard — eliminates TOCTOU race
 *    that SELECT-then-INSERT would have under concurrent retries
 * 2. Optimistic locking (version column): detects two concurrent threads
 *    both trying to markSent() the same row
 * 3. Publisher confirms before DB commit: if Rabbit publish fails we
 *    rollback — no ghost messages
 * 4. Consumer dedup: UPDATE status='PROCESSING' WHERE status='SENT'
 *    — only one consumer wins, rest skip safely
 *
 * DDL:
 *   CREATE TABLE commands (
 *     request_id TEXT        PRIMARY KEY,
 *     door_id    TEXT        NOT NULL,
 *     operation  TEXT        NOT NULL CHECK (operation IN ('LOCK','UNLOCK')),
 *     status     TEXT        NOT NULL DEFAULT 'PENDING',
 *     version    INT         NOT NULL DEFAULT 0,
 *     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
 *   );
 */

public record Command(String requestId, String doorId, String op) {
    public Command {
        if (!op.equals("LOCK") && !op.equals("UNLOCK"))
            throw new IllegalArgumentException("op must be LOCK or UNLOCK");
    }
}

