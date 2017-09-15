package org.limeprotocol.client;

import org.limeprotocol.*;
import org.limeprotocol.network.ChannelBase;
import org.limeprotocol.network.ChannelCommandProcessor;
import org.limeprotocol.network.ChannelCommandProcessorImpl;
import org.limeprotocol.network.Transport;
import org.limeprotocol.network.modules.NotifyReceiptChannelModule;
import org.limeprotocol.security.Authentication;

import java.io.IOException;

import static org.limeprotocol.Session.SessionState.*;

public class ClientChannelImpl extends ChannelBase implements ClientChannel {

    public ClientChannelImpl(Transport transport) {
        this(transport, false);
    }

    public ClientChannelImpl(Transport transport, boolean fillEnvelopeRecipients) {
        this(transport, fillEnvelopeRecipients, false);
    }

    public ClientChannelImpl(Transport transport, boolean fillEnvelopeRecipients, boolean autoReplyPings) {
        this(transport, fillEnvelopeRecipients, autoReplyPings, false);
    }

    public ClientChannelImpl(Transport transport, boolean fillEnvelopeRecipients, boolean autoReplyPings,
                             boolean autoNotifyReceipt) {
        this(transport, fillEnvelopeRecipients, autoReplyPings, autoNotifyReceipt, 0, 0);
    }

    public ClientChannelImpl(
            Transport transport,
            boolean fillEnvelopeRecipients,
            boolean autoReplyPings,
            boolean autoNotifyReceipt,
            long pingInterval,
            long pingDisconnectionInterval) {
        this(transport, fillEnvelopeRecipients, autoReplyPings, autoNotifyReceipt, pingInterval, pingDisconnectionInterval, new ChannelCommandProcessorImpl());

    }

    public ClientChannelImpl(
            Transport transport,
            boolean fillEnvelopeRecipients,
            boolean autoReplyPings,
            boolean autoNotifyReceipt,
            long pingInterval,
            long pingDisconnectionInterval,
            ChannelCommandProcessor channelCommandProcessor) {
        super(transport, fillEnvelopeRecipients, autoReplyPings, pingInterval, pingDisconnectionInterval, channelCommandProcessor);

        if (autoNotifyReceipt) {
            getMessageModules().add(new NotifyReceiptChannelModule(this));
        }
    }

    /**
     * Sends a new session envelope to the server and listen for the response.
     *
     * @param sessionListener
     */
    @Override
    public void startNewSession(SessionChannelListener sessionListener) throws IOException {
        if (getState() != NEW) {
            throw new IllegalStateException(String.format("Cannot start a session in the '%s' state.", getState()));
        }
        enqueueSessionListener(sessionListener);
        Session session = new Session();
        session.setState(NEW);
        sendSession(session);
    }

    /**
     * Sends a negotiate session envelope to accepts the session negotiation options
     * and listen for the server confirmation.
     *
     * @param sessionCompression
     * @param sessionEncryption
     * @param sessionListener
     */
    @Override
    public void negotiateSession(SessionCompression sessionCompression, SessionEncryption sessionEncryption, SessionChannelListener sessionListener) throws IOException {
        if (getState() != NEGOTIATING) {
            throw new IllegalStateException(String.format("Cannot negotiate a session in the '%s' state.", getState()));
        }
        enqueueSessionListener(sessionListener);
        Session session = new Session();
        session.setId(getSessionId());
        session.setState(NEGOTIATING);
        session.setCompression(sessionCompression);
        session.setEncryption(sessionEncryption);
        sendSession(session);
    }

    /**
     * Sends a authenticate session envelope to the server to establish an authenticated session
     * and listen for the established session envelope.
     *
     * @param identity
     * @param authentication
     * @param instance
     * @param sessionListener
     */
    @Override
    public void authenticateSession(Identity identity, Authentication authentication, String instance, SessionChannelListener sessionListener) throws IOException {
        if (getState() != AUTHENTICATING) {
            throw new IllegalStateException(String.format("Cannot authenticate a session in the '%s' state", getState()));
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity");
        }
        if (authentication == null) {
            throw new IllegalArgumentException("authentication");
        }
        enqueueSessionListener(sessionListener);
        Session session = new Session();
        session.setId(getSessionId());
        session.setFrom(new Node(identity.getName(), identity.getDomain(), instance));
        session.setState(AUTHENTICATING);
        session.setAuthentication(authentication);
        sendSession(session);
    }

    /**
     * Notify to the server that the specified message was received by the peer.
     *  @param messageId
     * @param to
     */
    @Override
    public void sendReceivedNotification(final String messageId, final Node to) throws IOException {
        if (to == null) {
            throw new IllegalArgumentException("to");
        }

        Notification notification = new Notification() {{
            setId(messageId);
            setTo(to);
            setEvent(Event.RECEIVED);
        }};

        sendNotification(notification);
    }

    /**
     * Sends a finishing session envelope to the server.
     */
    @Override
    public void sendFinishingSession() throws IOException {
        if (getState() != ESTABLISHED) {
            throw new IllegalStateException(String.format("Cannot finish a session in the '%s' state", getState()));
        }

        Session session = new Session() {{
            setId(getSessionId());
            setState(FINISHING);
        }};

        sendSession(session);
    }

    @Override
    public void establishSession(SessionCompression compression, SessionEncryption encryption,
                                 Identity identity, Authentication authentication, String instance,
                                 EstablishSessionListener listener) throws IOException {
        if (getState() != NEW) {
            throw new IllegalStateException(String.format("Cannot establish a session in the '%s' state", getState()));
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity");
        }
        if (authentication == null) {
            throw new IllegalArgumentException("authentication");
        }

        SessionEstablishing establishingListener = new SessionEstablishing(this, compression, encryption, identity,
                authentication, instance, listener);

        startNewSession(establishingListener);
    }

    @Override
    protected synchronized void raiseOnReceiveSession(Session session) {
        setSessionId(session.getId());
        setState(session.getState());

        if (session.getState() == ESTABLISHED) {
            setLocalNode(session.getTo());
            setRemoteNode(session.getFrom());
        } else if (session.getState() == FINISHED || session.getState() == FAILED) {
            try {
                getTransport().close();
            } catch (IOException e) {
                throw new RuntimeException("An error occurred while closing the transport", e);
            }
        }
        super.raiseOnReceiveSession(session);
    }

    private static class SessionEstablishing implements SessionChannelListener {

        private final ClientChannel channel;
        private SessionCompression compression;
        private SessionEncryption encryption;
        private final Identity identity;
        private final Authentication authentication;
        private final String instance;
        private final EstablishSessionListener listener;

        public SessionEstablishing(ClientChannel channel, SessionCompression compression, SessionEncryption encryption, Identity identity,
                                   Authentication authentication, String instance, EstablishSessionListener listener) {
            this.channel = channel;
            this.compression = compression;
            this.encryption = encryption;
            this.identity = identity;
            this.authentication = authentication;
            this.instance = instance;
            this.listener = listener;
        }

        @Override
        public void onReceiveSession(Session receivedSession) {
            try {
                if (receivedSession.getState() == NEGOTIATING) {
                    if (receivedSession.getCompressionOptions() != null) {
                        // Send desired options
                        SessionCompression selectedCompression = compression;
                        if (selectedCompression == null) {
                            selectedCompression = receivedSession.getCompressionOptions()[0];
                        }

                        SessionEncryption selectEncryption = encryption;
                        if (selectEncryption == null) {
                            selectEncryption = receivedSession.getEncryptionOptions()[0];
                        }

                        try {
                            channel.negotiateSession(selectedCompression, selectEncryption, this);
                        } catch (Exception e) {
                            this.listener.onFailure(e);
                        }
                    } else {
                        // Configure transport
                        if (receivedSession.getCompression() != channel.getTransport().getCompression()) {
                            channel.getTransport().setCompression(receivedSession.getCompression());
                        }
                        if (receivedSession.getEncryption() != channel.getTransport().getEncryption()) {
                            channel.getTransport().setEncryption(receivedSession.getEncryption());
                        }
                        channel.enqueueSessionListener(this);
                    }
                } else if (receivedSession.getState() == AUTHENTICATING) {
                    channel.authenticateSession(identity, authentication, instance, this);
                } else {
                    this.listener.onReceiveSession(receivedSession);
                }
            } catch (Exception e) {
                this.listener.onFailure(e);
            }
        }
    }
}
