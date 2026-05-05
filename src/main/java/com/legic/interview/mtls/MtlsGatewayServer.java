package com.legic.interview.mtls;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.security.auth.x500.X500Principal;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. mTLS: both sides present X.509 certs — server verifies client cert chain
 *    against a trusted CA, then we additionally pin by SHA-256 thumbprint
 * 2. setNeedClientAuth(true): TLS handshake fails if client sends no cert.
 *    setWantClientAuth(true) would continue without one — wrong for IoT.
 * 3. Certificate pinning: CA trust alone allows ANY cert the CA signed.
 *    Pinning ties trust to individual device certs — one compromised CA ≠ breach.
 * 4. CN extraction: device ID in cert Subject CN (e.g., CN=device-007) — no
 *    separate API key needed, identity is in the certificate itself.
 */

public class MtlsGatewayServer {

    // SHA-256 hex thumbprints of provisioned device certs (loaded at startup)
    private final Set<String> pinnedThumbprints = ConcurrentHashMap.newKeySet();

    public void start() throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        SSLContext sslCtx = buildSslContext(
                "server-keystore.p12",    "changeit",
                "device-ca-truststore.p12", "changeit");

        server.setHttpsConfigurator(new HttpsConfigurator(sslCtx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters ssl = sslCtx.getDefaultSSLParameters();
                // REQUIRE: handshake fails immediately without client cert
                ssl.setNeedClientAuth(true);
                ssl.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                ssl.setCipherSuites(new String[]{
                        "TLS_AES_256_GCM_SHA384",               // TLS 1.3
                        "TLS_CHACHA20_POLY1305_SHA256",          // TLS 1.3
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" // TLS 1.2 fallback
                });
                params.setSSLParameters(ssl);
            }
        });

        server.createContext("/api/data", ex -> {
            try {
                SSLSession   session  = ((HttpsExchange) ex).getSSLSession();
                java.security.cert.Certificate[] peers  = session.getPeerCertificates();
                X509Certificate cert = (X509Certificate) peers[0];

                // 1. Pinning check: reject devices not explicitly provisioned
                String thumbprint = sha256Thumbprint(cert);
                if (!pinnedThumbprints.contains(thumbprint)) {
                    send(ex, 403, "Unpinned certificate: " + thumbprint);
                    return;
                }

                // 2. Validity: reject expired certs even if pinned
                cert.checkValidity(); // throws CertificateExpiredException

                // 3. Extract device identity — no separate API key required
                String deviceId = extractCN(cert.getSubjectX500Principal());
                if (deviceId == null) { send(ex, 400, "Missing CN in cert"); return; }

                // 4. (Production) OCSP revocation — blocking is fine on virtual thread
                // OCSPChecker.check(cert, issuerCert);

                send(ex, 200, "Authenticated device: " + deviceId);

            } catch (SSLPeerUnverifiedException e) {
                send(ex, 401, "Client certificate required");
            } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                send(ex, 401, "Certificate expired or not yet valid");
            } catch (Exception e) {
                send(ex, 500, "Internal server error");
            }
        });

        server.start();
        System.out.println("mTLS server listening on :8443");
    }

    /** DER-encode cert, hash with SHA-256, hex-encode — standard fingerprint format */
    private String sha256Thumbprint(X509Certificate cert) throws Exception {
        byte[] der    = cert.getEncoded();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(der);
        return HexFormat.of().formatHex(digest);
    }

    /** Parse CN= segment from X.500 Distinguished Name string */
    private String extractCN(X500Principal principal) {
        for (String part : principal.getName().split(",")) {
            String t = part.trim();
            if (t.startsWith("CN=")) return t.substring(3);
        }
        return null;
    }

    private SSLContext buildSslContext(
            String ksPath, String ksPass, String tsPath, String tsPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(Files.newInputStream(Path.of(ksPath)), ksPass.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, ksPass.toCharArray());

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(Files.newInputStream(Path.of(tsPath)), tsPass.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    private void send(HttpExchange ex, int status, String msg) throws java.io.IOException {
        byte[] body = msg.getBytes();
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }

    /** Called during device provisioning ceremony */
    public void pinCertificate(X509Certificate cert) throws Exception {
        pinnedThumbprints.add(sha256Thumbprint(cert));
    }
}
