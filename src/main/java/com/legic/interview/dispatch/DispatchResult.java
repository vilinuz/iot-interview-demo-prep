package com.legic.interview.dispatch;

import com.rabbitmq.client.*;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.concurrent.*;

public sealed interface DispatchResult
        permits DispatchResult.Sent, DispatchResult.Duplicate, DispatchResult.Failed {
    record Sent(String requestId)                        implements DispatchResult {}
    record Duplicate(String requestId, String status)    implements DispatchResult {}
    record Failed(String requestId, String reason)       implements DispatchResult {}
}

// ── Dispatcher ────────────────────────────────────────────────────────────────
