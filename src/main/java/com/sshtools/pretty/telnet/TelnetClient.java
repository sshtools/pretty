package com.sshtools.pretty.telnet;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A standalone telnet client implementing the core telnet protocol (RFC 854,
 * RFC 855) with support for commonly used options including:
 * <ul>
 *   <li>NAWS - Negotiate About Window Size (RFC 1073)</li>
 *   <li>ECHO - Echo (RFC 857)</li>
 *   <li>SGA - Suppress Go Ahead (RFC 858)</li>
 *   <li>TTYPE - Terminal Type (RFC 1091)</li>
 *   <li>NEW-ENVIRON - New Environment Option (RFC 1572)</li>
 *   <li>BINARY - Binary Transmission (RFC 856)</li>
 * </ul>
 * <p>
 * This class handles the low-level telnet negotiation and provides hooks via
 * {@link TelnetListener} for higher-level integration with terminal emulators,
 * command frameworks, etc.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * var client = TelnetClient.builder("myhost.example.com")
 *     .withPort(23)
 *     .withTerminalType("xterm-256color")
 *     .withWindowSize(80, 24)
 *     .withListener(myListener)
 *     .build();
 *
 * client.connect();
 * // read decoded data from client.dataInputStream()
 * // write user input via client.send(...)
 * client.close();
 * </pre>
 */
public final class TelnetClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TelnetClient.class);

    // -----------------------------------------------------------------------
    // Telnet protocol constants
    // -----------------------------------------------------------------------

    /** Interpret As Command */
    public static final int IAC  = 255;
    /** End of sub-negotiation */
    public static final int SE   = 240;
    /** No Operation */
    public static final int NOP  = 241;
    /** Data Mark */
    public static final int DM   = 242;
    /** Break */
    public static final int BRK  = 243;
    /** Interrupt Process */
    public static final int IP   = 244;
    /** Abort Output */
    public static final int AO   = 245;
    /** Are You There */
    public static final int AYT  = 246;
    /** Erase Character */
    public static final int EC   = 247;
    /** Erase Line */
    public static final int EL   = 248;
    /** Go Ahead */
    public static final int GA   = 249;
    /** Start of sub-negotiation */
    public static final int SB   = 250;
    /** Will (option) */
    public static final int WILL = 251;
    /** Won't (option) */
    public static final int WONT = 252;
    /** Do (option) */
    public static final int DO   = 253;
    /** Don't (option) */
    public static final int DONT = 254;

    // -----------------------------------------------------------------------
    // Telnet option codes
    // -----------------------------------------------------------------------

    /**
     * Well-known telnet option codes.
     */
    public enum TelnetOption {
        /** Binary Transmission (RFC 856) */
        BINARY(0),
        /** Echo (RFC 857) */
        ECHO(1),
        /** Reconnection */
        RECONNECTION(2),
        /** Suppress Go Ahead (RFC 858) */
        SUPPRESS_GO_AHEAD(3),
        /** Status (RFC 859) */
        STATUS(5),
        /** Timing Mark (RFC 860) */
        TIMING_MARK(6),
        /** Terminal Type (RFC 1091) */
        TERMINAL_TYPE(24),
        /** Negotiate About Window Size (RFC 1073) */
        NAWS(31),
        /** Terminal Speed (RFC 1079) */
        TERMINAL_SPEED(32),
        /** Toggle Flow Control (RFC 1372) */
        TOGGLE_FLOW_CONTROL(33),
        /** Line Mode (RFC 1184) */
        LINEMODE(34),
        /** New Environment (RFC 1572) */
        NEW_ENVIRON(39);

        private final int code;

        TelnetOption(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Optional<TelnetOption> fromCode(int code) {
            for (var opt : values()) {
                if (opt.code == code) {
                    return Optional.of(opt);
                }
            }
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // Sub-negotiation constants for TTYPE
    // -----------------------------------------------------------------------
    private static final int TTYPE_IS   = 0;
    private static final int TTYPE_SEND = 1;

    // -----------------------------------------------------------------------
    // Sub-negotiation constants for NEW-ENVIRON
    // -----------------------------------------------------------------------
    private static final int ENVIRON_IS      = 0;
    private static final int ENVIRON_SEND    = 1;
    private static final int ENVIRON_INFO    = 2;
    private static final int ENVIRON_VAR     = 0;
    private static final int ENVIRON_VALUE   = 1;
    private static final int ENVIRON_USERVAR = 3;

    // -----------------------------------------------------------------------
    // Listener
    // -----------------------------------------------------------------------

    /**
     * Listener for telnet protocol events. All callbacks are invoked on the
     * reader thread unless otherwise noted.
     */
    public interface TelnetListener {

        /**
         * Called when the server's echo mode changes. When remote echo is
         * enabled, the client should suppress local echo.
         *
         * @param remoteEcho {@code true} if the server will echo
         */
        default void onEchoChanged(boolean remoteEcho) {}

        /**
         * Called when the server requests a window-size update via NAWS.
         * This is typically sent once after NAWS negotiation succeeds and
         * the client should respond with the current window dimensions.
         */
        default void onWindowSizeRequested() {}

        /**
         * Called when the connection has been established and initial option
         * negotiation has been sent.
         */
        default void onConnected() {}

        /**
         * Called when the connection is closed, either by the server or locally.
         *
         * @param error the exception that caused disconnection, or empty if clean
         */
        default void onDisconnected(Optional<Exception> error) {}

        /**
         * Called when binary mode is enabled or disabled.
         *
         * @param binary {@code true} if binary mode is now active
         */
        default void onBinaryMode(boolean binary) {}

        /**
         * Called when Suppress Go Ahead is negotiated.
         *
         * @param suppressed {@code true} if Go Ahead is suppressed
         */
        default void onSuppressGoAhead(boolean suppressed) {}
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    /**
     * Create a new builder for the given hostname.
     *
     * @param hostname the remote host to connect to
     * @return a new builder
     */
    public static Builder builder(String hostname) {
        return new Builder(hostname);
    }

    public static final class Builder {
        private final String hostname;
        private int port = 23;
        private String terminalType = "xterm-256color";
        private int columns = 80;
        private int rows = 24;
        private Charset charset = StandardCharsets.UTF_8;
        private int connectTimeoutMs = 30_000;
        private int soTimeoutMs = 0;
        private final Set<TelnetOption> supportedOptions = EnumSet.of(
                TelnetOption.ECHO,
                TelnetOption.SUPPRESS_GO_AHEAD,
                TelnetOption.TERMINAL_TYPE,
                TelnetOption.NAWS,
                TelnetOption.BINARY,
                TelnetOption.NEW_ENVIRON
        );
        private final List<TelnetListener> listeners = new CopyOnWriteArrayList<>();

        private Builder(String hostname) {
            this.hostname = Objects.requireNonNull(hostname, "hostname");
        }

        public Builder withPort(int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port: " + port);
            this.port = port;
            return this;
        }

        public Builder withTerminalType(String terminalType) {
            this.terminalType = Objects.requireNonNull(terminalType);
            return this;
        }

        public Builder withWindowSize(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
            return this;
        }

        public Builder withCharset(Charset charset) {
            this.charset = Objects.requireNonNull(charset);
            return this;
        }

        public Builder withConnectTimeout(int ms) {
            this.connectTimeoutMs = ms;
            return this;
        }

        public Builder withSoTimeout(int ms) {
            this.soTimeoutMs = ms;
            return this;
        }

        /**
         * Add a supported option for negotiation. The default set already
         * includes ECHO, SGA, TTYPE, NAWS, BINARY, and NEW_ENVIRON.
         */
        public Builder withSupportedOption(TelnetOption option) {
            this.supportedOptions.add(option);
            return this;
        }

        public Builder withoutSupportedOption(TelnetOption option) {
            this.supportedOptions.remove(option);
            return this;
        }

        public Builder withListener(TelnetListener listener) {
            this.listeners.add(Objects.requireNonNull(listener));
            return this;
        }

        public TelnetClient build() {
            return new TelnetClient(this);
        }
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final String hostname;
    private final int port;
    private final String terminalType;
    private int columns;
    private int rows;
    private final Charset charset;
    private final int connectTimeoutMs;
    private final int soTimeoutMs;
    private final Set<TelnetOption> supportedOptions;
    private final List<TelnetListener> listeners;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private volatile boolean connected;
    private volatile boolean remoteEcho;
    private volatile boolean suppressGoAhead;
    private volatile boolean binaryMode;
    private volatile boolean nawsEnabled;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    private TelnetClient(Builder builder) {
        this.hostname = builder.hostname;
        this.port = builder.port;
        this.terminalType = builder.terminalType;
        this.columns = builder.columns;
        this.rows = builder.rows;
        this.charset = builder.charset;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.soTimeoutMs = builder.soTimeoutMs;
        this.supportedOptions = EnumSet.copyOf(builder.supportedOptions);
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Add a listener after construction.
     */
    public void addListener(TelnetListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Remove a previously added listener.
     */
    public void removeListener(TelnetListener listener) {
        listeners.remove(listener);
    }

    /**
     * Open a TCP connection to the remote host and begin telnet option
     * negotiation. After this method returns the caller should begin reading
     * from {@link #processAndDecode(OutputStream)} or the raw socket.
     */
    public void connect() throws IOException {
        if (connected) throw new IllegalStateException("Already connected.");
        LOG.info("Connecting to {}:{}", hostname, port);
        socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(hostname, port), connectTimeoutMs);
        if (soTimeoutMs > 0) {
            socket.setSoTimeout(soTimeoutMs);
        }
        in = socket.getInputStream();
        out = socket.getOutputStream();
        connected = true;
        LOG.info("Connected to {}:{}", hostname, port);

        sendInitialNegotiation();
        fireConnected();
    }

    /**
     * Read from the telnet socket, process IAC commands, and write decoded
     * application data to the given {@link OutputStream}. This method blocks
     * until the connection is closed or an error occurs, making it suitable
     * for use on a dedicated reader thread (similar to how
     * {@code SshProtocol.decode()} and {@code SerialProtocol.decode()} work).
     *
     * @param decoded the stream to receive decoded (non-telnet) data
     * @throws IOException on I/O error
     */
    public void processAndDecode(OutputStream decoded) throws IOException {
        var buf = new byte[4096];
        try {
            while (connected) {
                var b = in.read();
                if (b == -1) {
                    break;
                }
                if (b == IAC) {
                    handleIAC();
                } else {
                    decoded.write(b);
                    decoded.flush();
                }
            }
        } catch (IOException e) {
            if (connected) {
                throw e;
            }
            // else we were closed locally, suppress
        } finally {
            fireDisconnected(Optional.empty());
        }
    }

    /**
     * Send raw user data to the remote host. If the data contains a literal
     * 0xFF byte it is escaped as IAC IAC per the telnet protocol.
     *
     * @param data the bytes to send
     * @param off  offset into the array
     * @param len  number of bytes to send
     */
    public void send(byte[] data, int off, int len) throws IOException {
        requireConnected();
        synchronized (out) {
            for (int i = off; i < off + len; i++) {
                int b = data[i] & 0xFF;
                if (b == IAC) {
                    out.write(IAC);
                    out.write(IAC);
                } else {
                    out.write(b);
                }
            }
            out.flush();
        }
    }

    /**
     * Convenience overload to send all bytes from the array.
     */
    public void send(byte[] data) throws IOException {
        send(data, 0, data.length);
    }

    /**
     * Notify the server of a terminal window size change. This sends a NAWS
     * sub-negotiation if NAWS has been successfully negotiated.
     *
     * @param columns new column count
     * @param rows    new row count
     */
    public void sendWindowSize(int columns, int rows) throws IOException {
        this.columns = columns;
        this.rows = rows;
        if (nawsEnabled && connected) {
            doSendNaws();
        }
    }

    /**
     * Send a telnet Break command to the remote host.
     */
    public void sendBreak() throws IOException {
        requireConnected();
        sendCommand(BRK);
    }

    /**
     * Send an Are-You-There command.
     */
    public void sendAreYouThere() throws IOException {
        requireConnected();
        sendCommand(AYT);
    }

    /**
     * Send an Interrupt Process command.
     */
    public void sendInterruptProcess() throws IOException {
        requireConnected();
        sendCommand(IP);
    }

    /**
     * @return {@code true} if currently connected
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * @return {@code true} if the remote side is echoing characters
     */
    public boolean isRemoteEcho() {
        return remoteEcho;
    }

    /**
     * @return {@code true} if binary mode has been negotiated
     */
    public boolean isBinaryMode() {
        return binaryMode;
    }

    /**
     * @return {@code true} if Suppress Go Ahead has been negotiated
     */
    public boolean isSuppressGoAhead() {
        return suppressGoAhead;
    }

    /**
     * @return the remote hostname
     */
    public String hostname() {
        return hostname;
    }

    /**
     * @return the remote port
     */
    public int port() {
        return port;
    }

    /**
     * @return the negotiated or configured terminal type
     */
    public String terminalType() {
        return terminalType;
    }

    /**
     * @return the current column count
     */
    public int columns() {
        return columns;
    }

    /**
     * @return the current row count
     */
    public int rows() {
        return rows;
    }

    /**
     * @return the character set for encoding
     */
    public Charset charset() {
        return charset;
    }

    @Override
    public void close() throws IOException {
        if (!connected) return;
        connected = false;
        LOG.info("Closing telnet connection to {}:{}", hostname, port);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } finally {
            socket = null;
            in = null;
            out = null;
        }
    }

    // -----------------------------------------------------------------------
    // Initial negotiation
    // -----------------------------------------------------------------------

    private void sendInitialNegotiation() throws IOException {
        if (supportedOptions.contains(TelnetOption.NAWS)) {
            sendWill(TelnetOption.NAWS);
        }
        if (supportedOptions.contains(TelnetOption.TERMINAL_TYPE)) {
            sendWill(TelnetOption.TERMINAL_TYPE);
        }
        if (supportedOptions.contains(TelnetOption.NEW_ENVIRON)) {
            sendWill(TelnetOption.NEW_ENVIRON);
        }
        if (supportedOptions.contains(TelnetOption.SUPPRESS_GO_AHEAD)) {
            sendDo(TelnetOption.SUPPRESS_GO_AHEAD);
        }
        if (supportedOptions.contains(TelnetOption.ECHO)) {
            sendDo(TelnetOption.ECHO);
        }
        if (supportedOptions.contains(TelnetOption.BINARY)) {
            sendDo(TelnetOption.BINARY);
            sendWill(TelnetOption.BINARY);
        }
    }

    // -----------------------------------------------------------------------
    // IAC handling
    // -----------------------------------------------------------------------

    private void handleIAC() throws IOException {
        int cmd = readByte();
        switch (cmd) {
            case WILL:
                handleWill(readByte());
                break;
            case WONT:
                handleWont(readByte());
                break;
            case DO:
                handleDo(readByte());
                break;
            case DONT:
                handleDont(readByte());
                break;
            case SB:
                handleSubNegotiation();
                break;
            case IAC:
                // Escaped 0xFF — should be passed to application data.
                // Caller can handle if needed; for now we just ignore inline.
                break;
            case NOP:
            case GA:
                // Ignore
                break;
            case DM:
            case BRK:
            case IP:
            case AO:
            case AYT:
            case EC:
            case EL:
                LOG.debug("Received telnet command: {}", cmd);
                break;
            default:
                LOG.warn("Unknown telnet command: {}", cmd);
                break;
        }
    }

    private void handleWill(int option) throws IOException {
        var opt = TelnetOption.fromCode(option);
        LOG.debug("Received WILL {}", opt.map(Enum::name).orElse(String.valueOf(option)));

        if (opt.isPresent()) {
            switch (opt.get()) {
                case ECHO:
                    sendDo(opt.get());
                    remoteEcho = true;
                    fireEchoChanged(true);
                    break;
                case SUPPRESS_GO_AHEAD:
                    sendDo(opt.get());
                    suppressGoAhead = true;
                    fireSuppressGoAhead(true);
                    break;
                case BINARY:
                    sendDo(opt.get());
                    binaryMode = true;
                    fireBinaryMode(true);
                    break;
                default:
                    // We don't want the server to enable this option
                    sendDont(option);
                    break;
            }
        } else {
            sendDont(option);
        }
    }

    private void handleWont(int option) throws IOException {
        var opt = TelnetOption.fromCode(option);
        LOG.debug("Received WONT {}", opt.map(Enum::name).orElse(String.valueOf(option)));

        if (opt.isPresent()) {
            switch (opt.get()) {
                case ECHO:
                    remoteEcho = false;
                    fireEchoChanged(false);
                    break;
                case SUPPRESS_GO_AHEAD:
                    suppressGoAhead = false;
                    fireSuppressGoAhead(false);
                    break;
                case BINARY:
                    binaryMode = false;
                    fireBinaryMode(false);
                    break;
                default:
                    break;
            }
        }
    }

    private void handleDo(int option) throws IOException {
        var opt = TelnetOption.fromCode(option);
        LOG.debug("Received DO {}", opt.map(Enum::name).orElse(String.valueOf(option)));

        if (opt.isPresent() && supportedOptions.contains(opt.get())) {
            switch (opt.get()) {
                case NAWS:
                    sendWill(opt.get());
                    nawsEnabled = true;
                    doSendNaws();
                    fireWindowSizeRequested();
                    break;
                case TERMINAL_TYPE:
                    sendWill(opt.get());
                    break;
                case NEW_ENVIRON:
                    sendWill(opt.get());
                    break;
                case BINARY:
                    sendWill(opt.get());
                    binaryMode = true;
                    fireBinaryMode(true);
                    break;
                case SUPPRESS_GO_AHEAD:
                    sendWill(opt.get());
                    suppressGoAhead = true;
                    fireSuppressGoAhead(true);
                    break;
                default:
                    sendWill(opt.get());
                    break;
            }
        } else {
            sendWont(option);
        }
    }

    private void handleDont(int option) throws IOException {
        var opt = TelnetOption.fromCode(option);
        LOG.debug("Received DONT {}", opt.map(Enum::name).orElse(String.valueOf(option)));

        if (opt.isPresent()) {
            switch (opt.get()) {
                case NAWS:
                    nawsEnabled = false;
                    break;
                case BINARY:
                    binaryMode = false;
                    fireBinaryMode(false);
                    break;
                default:
                    break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sub-negotiation
    // -----------------------------------------------------------------------

    private void handleSubNegotiation() throws IOException {
        int option = readByte();
        var data = readUntilSE();
        var opt = TelnetOption.fromCode(option);
        LOG.debug("Sub-negotiation for {}", opt.map(Enum::name).orElse(String.valueOf(option)));

        if (opt.isPresent()) {
            switch (opt.get()) {
                case TERMINAL_TYPE:
                    if (data.length > 0 && data[0] == TTYPE_SEND) {
                        sendTerminalType();
                    }
                    break;
                case NEW_ENVIRON:
                    if (data.length > 0 && data[0] == ENVIRON_SEND) {
                        sendEnvironment(data);
                    }
                    break;
                default:
                    LOG.debug("Unhandled sub-negotiation for option {}", opt.get());
                    break;
            }
        }
    }

    /**
     * Read bytes until IAC SE is encountered. Returns the payload between SB
     * option and SE.
     */
    private byte[] readUntilSE() throws IOException {
        var buf = new java.io.ByteArrayOutputStream();
        boolean lastWasIAC = false;
        while (true) {
            int b = readByte();
            if (lastWasIAC) {
                if (b == SE) {
                    break;
                } else if (b == IAC) {
                    // Escaped IAC inside sub-negotiation
                    buf.write(IAC);
                    lastWasIAC = false;
                } else {
                    // Shouldn't happen in well-formed data
                    LOG.warn("Unexpected byte after IAC in sub-negotiation: {}", b);
                    lastWasIAC = false;
                }
            } else if (b == IAC) {
                lastWasIAC = true;
            } else {
                buf.write(b);
            }
        }
        return buf.toByteArray();
    }

    // -----------------------------------------------------------------------
    // Send helpers
    // -----------------------------------------------------------------------

    private void sendCommand(int cmd) throws IOException {
        synchronized (out) {
            out.write(IAC);
            out.write(cmd);
            out.flush();
        }
    }

    private void sendWill(TelnetOption option) throws IOException {
        LOG.debug("Sending WILL {}", option);
        synchronized (out) {
            out.write(new byte[] { (byte) IAC, (byte) WILL, (byte) option.code() });
            out.flush();
        }
    }

    private void sendWont(int option) throws IOException {
        LOG.debug("Sending WONT {}", option);
        synchronized (out) {
            out.write(new byte[] { (byte) IAC, (byte) WONT, (byte) option });
            out.flush();
        }
    }

    private void sendDo(TelnetOption option) throws IOException {
        LOG.debug("Sending DO {}", option);
        synchronized (out) {
            out.write(new byte[] { (byte) IAC, (byte) DO, (byte) option.code() });
            out.flush();
        }
    }

    private void sendDont(int option) throws IOException {
        LOG.debug("Sending DONT {}", option);
        synchronized (out) {
            out.write(new byte[] { (byte) IAC, (byte) DONT, (byte) option });
            out.flush();
        }
    }

    private void doSendNaws() throws IOException {
        LOG.debug("Sending NAWS {}x{}", columns, rows);
        synchronized (out) {
            out.write(new byte[] {
                (byte) IAC, (byte) SB, (byte) TelnetOption.NAWS.code(),
                (byte) ((columns >> 8) & 0xFF), (byte) (columns & 0xFF),
                (byte) ((rows >> 8) & 0xFF), (byte) (rows & 0xFF),
                (byte) IAC, (byte) SE
            });
            out.flush();
        }
    }

    private void sendTerminalType() throws IOException {
        LOG.debug("Sending terminal type: {}", terminalType);
        var typeBytes = terminalType.getBytes(StandardCharsets.US_ASCII);
        synchronized (out) {
            out.write(IAC);
            out.write(SB);
            out.write(TelnetOption.TERMINAL_TYPE.code());
            out.write(TTYPE_IS);
            out.write(typeBytes);
            out.write(IAC);
            out.write(SE);
            out.flush();
        }
    }

    private void sendEnvironment(byte[] requestData) throws IOException {
        LOG.debug("Sending environment response");
        synchronized (out) {
            out.write(IAC);
            out.write(SB);
            out.write(TelnetOption.NEW_ENVIRON.code());
            out.write(ENVIRON_IS);
            // Send empty response — no environment variables to disclose by default.
            // Sub-classes or listeners can override via additional sub-negotiation if needed.
            out.write(IAC);
            out.write(SE);
            out.flush();
        }
    }

    // -----------------------------------------------------------------------
    // I/O helpers
    // -----------------------------------------------------------------------

    private int readByte() throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new IOException("Unexpected end of stream from " + hostname + ":" + port);
        }
        return b;
    }

    private void requireConnected() {
        if (!connected || out == null) {
            throw new IllegalStateException("Not connected.");
        }
    }

    // -----------------------------------------------------------------------
    // Listener firing
    // -----------------------------------------------------------------------

    private void fireConnected() {
        for (var l : listeners) {
            try { l.onConnected(); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    private void fireDisconnected(Optional<Exception> error) {
        for (var l : listeners) {
            try { l.onDisconnected(error); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    private void fireEchoChanged(boolean echo) {
        for (var l : listeners) {
            try { l.onEchoChanged(echo); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    private void fireWindowSizeRequested() {
        for (var l : listeners) {
            try { l.onWindowSizeRequested(); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    private void fireBinaryMode(boolean binary) {
        for (var l : listeners) {
            try { l.onBinaryMode(binary); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    private void fireSuppressGoAhead(boolean suppressed) {
        for (var l : listeners) {
            try { l.onSuppressGoAhead(suppressed); } catch (Exception e) { LOG.warn("Listener error", e); }
        }
    }

    @Override
    public String toString() {
        return "TelnetClient[" + hostname + ":" + port + ", connected=" + connected + "]";
    }
}
