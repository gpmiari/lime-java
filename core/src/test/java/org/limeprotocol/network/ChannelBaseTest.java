package org.limeprotocol.network;

import org.junit.Test;
import org.limeprotocol.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.limeprotocol.testHelpers.Dummy.*;
import static org.mockito.Mockito.*;

public class ChannelBaseTest {

    private TestTransport transport;
    private SessionChannel.SessionChannelListener sessionChannelListener;

    private ChannelBase getTarget(Session.SessionState state) {
        return getTarget(state, false);
    }

    private ChannelBase getTarget(Session.SessionState state, boolean fillEnvelopeRecipients) {
        return getTarget(state, fillEnvelopeRecipients, null, null);
    }

    private ChannelBase getTarget(Session.SessionState state, boolean fillEnvelopeRecipients, Node remoteNode, Node localNode) {
        return getTarget(state, fillEnvelopeRecipients, remoteNode, localNode, null);
    }

    private ChannelBase getTarget(Session.SessionState state, boolean fillEnvelopeRecipients, Node remoteNode, Node localNode, String sessionId) {
        return getTarget(state, fillEnvelopeRecipients, false, remoteNode, localNode, sessionId);
    }
    
    private ChannelBase getTarget(Session.SessionState state, boolean fillEnvelopeRecipients, boolean autoReplyPings, Node remoteNode, Node localNode, String sessionId) {
        return getTarget(state, fillEnvelopeRecipients, autoReplyPings, 0, 0, remoteNode, localNode, sessionId);
    }

    private ChannelBase getTarget(Session.SessionState state, boolean fillEnvelopeRecipients, boolean autoReplyPings, long pingInterval, long pingDisconnectionInterval, Node remoteNode, Node localNode, String sessionId) {
        transport = new TestTransport();
        sessionChannelListener = mock(SessionChannel.SessionChannelListener.class);
        ChannelBase channelBase = new TestChannel(transport, state, fillEnvelopeRecipients, autoReplyPings, pingInterval, pingDisconnectionInterval, remoteNode, localNode, sessionId);
        channelBase.enqueueSessionListener(sessionChannelListener);
        return channelBase;
    }

    @Test
    public void sendCommand_establishedState_callsTransport() throws IOException {
        // Arrange
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendCommand(command);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(command, transport.sentEnvelopes.remove());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendCommand_nullCommand_throwsIllegalArgumentException() throws IOException {
        // Arrange
        Command command = null;
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendCommand(command);
    }

    @Test(expected = IllegalStateException.class)
    public void sendCommand_newCommand_throwsIllegalStateException() throws IOException {
        // Arrange
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.NEW);

        // Act
        target.sendCommand(command);
    }

    @Test
    public void sendCommand_moduleReturnsCommand_sendsModuleCommand() throws IOException {
        // Arrange
        Command command = createCommand(createPlainDocument());
        Command moduleCommand = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Command> module = mock(ChannelModule.class);
        when(module.onSending(command)).thenReturn(moduleCommand);
        target.getCommandModules().add(module);

        // Act
        target.sendCommand(command);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(moduleCommand, transport.sentEnvelopes.remove());
        verify(module, times(1)).onSending(command);
    }

    @Test
    public void sendCommand_moduleReturnsNull_doNotCallTransport() throws IOException {

        // Arrange
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Command> module = mock(ChannelModule.class);
        when(module.onSending(command)).thenReturn(null);
        target.getCommandModules().add(module);

        // Act
        target.sendCommand(command);

        // Assert
        assertEquals(0, transport.sentEnvelopes.size());
    }

    @Test
    public void sendCommand_multipleRegisteredModules_callsEachModuleOnce() throws IOException {
        // Arrange
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Command> module = mock(ChannelModule.class);
            when(module.onSending(command)).thenReturn(command);
            target.getCommandModules().add(module);
        }

        // Act
        target.sendCommand(command);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(command, transport.sentEnvelopes.remove());
        for (ChannelModule<Command> module : target.getCommandModules()) {
            verify(module, times(1)).onSending(command);
        }
    }

    @Test
    public void addCommandListener_callsTwiceForSameInstance_registerOnce() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.addCommandListener(listener, true);
        target.addCommandListener(listener, true);
        transport.raiseOnReceive(command);

        // Assert
        verify(listener, times(1)).onReceiveCommand(command);
    }

    @Test
    public void onReceiveCommand_registeredListenerTwoReceives_callsListenerAndUnregister() throws InterruptedException {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, true);

        // Act
        transport.raiseOnReceive(command);
        transport.raiseOnReceive(command);

        // Assert
        verify(listener, times(1)).onReceiveCommand(command);
    }

    @Test
    public void onReceiveCommand_registeredListenersMultipleReceives_callsListenersMultipleTimes() throws InterruptedException {
        // Arrange
        int commandCount = createRandomInt(100) + 1;
        int listenersCount = createRandomInt(10) + 1;
        
        // Arrange
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        List<CommandChannel.CommandChannelListener> listeners = new ArrayList<>();
        for (int i = 0; i < listenersCount; i++) {
            CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
            target.addCommandListener(listener, false);
            listeners.add(listener);
        }
        
        // Act
        for (int i = 0; i < commandCount; i++) {
            transport.raiseOnReceive(command);
        }

        // Assert
        for (int i = 0; i < listenersCount; i++) {
            verify(listeners.get(i), times(commandCount)).onReceiveCommand(command);
        }
    }

    @Test
    public void onReceiveCommand_autoReplyPings_callsSendCommandWithPingResponse() throws InterruptedException {
        // Arrange
        Command command = new Command(EnvelopeId.newId());
        command.setFrom(createNode());
        command.setMethod(Command.CommandMethod.GET);
        command.setUri(new LimeUri("/ping"));
        
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, false, true, null, null, null);

        // Act
        transport.raiseOnReceive(command);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        Envelope sentEnvelope = transport.sentEnvelopes.remove();
        assertTrue(sentEnvelope instanceof Command);
        Command sentCommand = (Command)sentEnvelope;
        assertEquals(command.getId(), sentCommand.getId());
        assertEquals(command.getFrom(), sentCommand.getTo());
        assertEquals(Command.CommandStatus.SUCCESS, sentCommand.getStatus());
        assertNotNull(sentCommand.getType());
        assertEquals("application/vnd.lime.ping+json", sentCommand.getType().toString());
    }

    @Test
    public void onReceiveCommand_moduleReturnsCommand_receivesModuleCommand() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        Command moduleCommand = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, true);
        ChannelModule<Command> module = mock(ChannelModule.class);
        when(module.onReceiving(command)).thenReturn(moduleCommand);
        target.getCommandModules().add(module);

        // Act
        transport.raiseOnReceive(command);

        // Assert
        verify(module, times(1)).onReceiving(command);
        verify(listener, times(1)).onReceiveCommand(moduleCommand);
    }

    @Test
    public void onReceiveCommand_moduleReturnsNull_ignoresCommand()  {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command1 = createCommand(createPlainDocument());
        Command command2 = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, true);
        ChannelModule<Command> module = mock(ChannelModule.class);
        when(module.onReceiving(command1)).thenReturn(null);
        when(module.onReceiving(command2)).thenReturn(command2);
        target.getCommandModules().add(module);

        // Act
        transport.raiseOnReceive(command1);
        transport.raiseOnReceive(command2);

        // Assert
        verify(module, times(1)).onReceiving(command1);
        verify(module, times(1)).onReceiving(command2);
        verify(listener, never()).onReceiveCommand(command1);
        verify(listener, times(1)).onReceiveCommand(command2);
    }

    @Test
    public void onReceiveCommand_multipleRegisteredModules_callsEachModuleOnce() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, true);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Command> module = mock(ChannelModule.class);
            when(module.onReceiving(command)).thenReturn(command);
            target.getCommandModules().add(module);
        }

        // Act
        transport.raiseOnReceive(command);

        // Assert
        verify(listener, times(1)).onReceiveCommand(command);
        for (ChannelModule<Command> module : target.getCommandModules()) {
            verify(module, times(1)).onReceiving(command);
        }
    }

    @Test
    public void sendMessage_establishedState_callsTransport() throws IOException {
        // Arrange
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        
        // Act
        target.sendMessage(message);
        
        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(message, transport.sentEnvelopes.remove());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendMessage_nullMessage_throwsIllegalArgumentException() throws IOException {
        // Arrange
        Message message = null;
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendMessage(message);
    }

    @Test(expected = IllegalStateException.class)
    public void sendMessage_newMessage_throwsIllegalStateException() throws IOException {
        // Arrange
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.NEW);

        // Act
        target.sendMessage(message);
    }

    @Test
    public void sendMessage_moduleReturnsMessage_sendsModuleMessage() throws IOException {
        // Arrange
        Message message = createMessage(createPlainDocument());
        Message moduleMessage = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Message> module = mock(ChannelModule.class);
        when(module.onSending(message)).thenReturn(moduleMessage);
        target.getMessageModules().add(module);

        // Act
        target.sendMessage(message);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(moduleMessage, transport.sentEnvelopes.remove());
    }

    @Test
    public void sendMessage_moduleReturnsNull_doNotCallTransport() throws IOException {
        // Arrange
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Message> module = mock(ChannelModule.class);
        when(module.onSending(message)).thenReturn(null);
        target.getMessageModules().add(module);

        // Act
        target.sendMessage(message);

        // Assert
        assertEquals(0, transport.sentEnvelopes.size());
    }

    @Test
    public void sendMessage_multipleRegisteredModules_callsEachModuleOnce() throws IOException {
        // Arrange
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Message> module = mock(ChannelModule.class);
            when(module.onSending(message)).thenReturn(message);
            target.getMessageModules().add(module);
        }

        // Act
        target.sendMessage(message);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(message, transport.sentEnvelopes.remove());
        for (ChannelModule<Message> module : target.getMessageModules()) {
            verify(module, times(1)).onSending(message);
        }
    }

    @Test
    public void onReceiveMessage_registeredListenerTwoReceives_callsListenerAndUnregister() throws InterruptedException {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, true);

        // Act
        transport.raiseOnReceive(message);
        transport.raiseOnReceive(message);

        // Assert
        verify(listener, times(1)).onReceiveMessage(message);
    }

    @Test
    public void onReceiveMessage_registeredListenersMultipleReceives_callsListenersMultipleTimes() throws InterruptedException {
        // Arrange
        int messageCount = createRandomInt(100) + 1;
        int listenersCount = createRandomInt(10) + 1;

        // Arrange
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        List<MessageChannel.MessageChannelListener> listeners = new ArrayList<>();
        for (int i = 0; i < listenersCount; i++) {
            MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
            target.addMessageListener(listener, false);
            listeners.add(listener);
        }

        // Act
        for (int i = 0; i < messageCount; i++) {
            transport.raiseOnReceive(message);
        }

        // Assert
        for (int i = 0; i < listenersCount; i++) {
            verify(listeners.get(i), times(messageCount)).onReceiveMessage(message);
        }
    }

    @Test
    public void onReceiveMessage_noRecipients_fillsFromTheSession() throws InterruptedException {
        // Arrange
        Node remoteNode = createNode();
        Node localNode = createNode();
        Message message = createMessage(createPlainDocument());
        message.setFrom(null);
        message.setTo(null);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, true, remoteNode, localNode);
        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        final List<Message> actual = new ArrayList<>();
        target.addMessageListener(new MessageChannel.MessageChannelListener() {

            @Override
            public void onReceiveMessage(Message message) {
                actual.add(message);
                synchronized (semaphore) {
                    semaphore.release();
                }
            }
        }, true);

        // Act
        transport.raiseOnReceive(message);
        synchronized (semaphore) {
            semaphore.tryAcquire(1, 1000, TimeUnit.MILLISECONDS);
        }

        // Assert
        assertEquals(1, actual.size());
        assertEquals(message, actual.get(0));
        assertEquals(localNode, actual.get(0).getTo());
        assertEquals(remoteNode, actual.get(0).getFrom());
        assertNull(actual.get(0).getPp());
    }

    @Test
    public void onReceiveMessage_incompleteRecipients_fillsFromTheSession() throws InterruptedException {
        // Arrange
        Node remoteNode = createNode();
        Node localNode = createNode();
        Message message = createMessage(createPlainDocument());
        message.setFrom(remoteNode.copy());
        message.setTo(localNode.copy());
        message.getFrom().setDomain(null);
        message.getTo().setDomain(null);
        message.getFrom().setInstance(null);
        message.getTo().setInstance(null);

        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, true, remoteNode, localNode);
        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        final List<Message> actual = new ArrayList<>();
        target.addMessageListener(new MessageChannel.MessageChannelListener() {

            @Override
            public void onReceiveMessage(Message message) {
                actual.add(message);
                synchronized (semaphore) {
                    semaphore.release();
                }
            }
        }, true);

        // Act
        transport.raiseOnReceive(message);
        synchronized (semaphore) {
            semaphore.tryAcquire(1, 1000, TimeUnit.MILLISECONDS);
        }

        // Assert
        assertEquals(1, actual.size());
        assertEquals(message, actual.get(0));
        assertEquals(localNode.toIdentity(), actual.get(0).getTo().toIdentity());
        assertEquals(remoteNode.toIdentity(), actual.get(0).getFrom().toIdentity());
        assertNull(actual.get(0).getPp());
    }

    @Test
    public void onReceiveMessage_moduleReturnsMessage_receivesModuleMessage() {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        Message moduleMessage = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, true);
        ChannelModule<Message> module = mock(ChannelModule.class);
        when(module.onReceiving(message)).thenReturn(moduleMessage);
        target.getMessageModules().add(module);

        // Act
        transport.raiseOnReceive(message);

        // Assert
        verify(module, times(1)).onReceiving(message);
        verify(listener, times(1)).onReceiveMessage(moduleMessage);
    }

    @Test
    public void onReceiveMessage_moduleReturnsNull_ignoresMessage()  {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message1 = createMessage(createPlainDocument());
        Message message2 = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, true);
        ChannelModule<Message> module = mock(ChannelModule.class);
        when(module.onReceiving(message1)).thenReturn(null);
        when(module.onReceiving(message2)).thenReturn(message2);
        target.getMessageModules().add(module);

        // Act
        transport.raiseOnReceive(message1);
        transport.raiseOnReceive(message2);

        // Assert
        verify(module, times(1)).onReceiving(message1);
        verify(module, times(1)).onReceiving(message2);
        verify(listener, never()).onReceiveMessage(message1);
        verify(listener, times(1)).onReceiveMessage(message2);
    }

    @Test
    public void onReceiveMessage_multipleRegisteredModules_callsEachModuleOnce() {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, true);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Message> module = mock(ChannelModule.class);
            when(module.onReceiving(message)).thenReturn(message);
            target.getMessageModules().add(module);
        }

        // Act
        transport.raiseOnReceive(message);

        // Assert
        verify(listener, times(1)).onReceiveMessage(message);
        for (ChannelModule<Message> module : target.getMessageModules()) {
            verify(module, times(1)).onReceiving(message);
        }
    }

    @Test
    public void sendNotification_establishedState_callsTransport() throws IOException {
        // Arrange
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendNotification(notification);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(notification, transport.sentEnvelopes.remove());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendNotification_nullNotification_throwsIllegalArgumentException() throws IOException {
        // Arrange
        Notification notification = null;
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendNotification(notification);
    }

    @Test(expected = IllegalStateException.class)
    public void sendNotification_newNotification_throwsIllegalStateException() throws IOException {
        // Arrange
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.NEW);

        // Act
        target.sendNotification(notification);
    }

    @Test
    public void sendNotification_moduleReturnsNotification_sendsModuleNotification() throws IOException {
        // Arrange
        Notification notification = createNotification(Notification.Event.AUTHORIZED);
        Notification moduleNotification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Notification> module = mock(ChannelModule.class);
        when(module.onSending(notification)).thenReturn(moduleNotification);
        target.getNotificationModules().add(module);

        // Act
        target.sendNotification(notification);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(moduleNotification, transport.sentEnvelopes.remove());
    }

    @Test
    public void sendNotification_moduleReturnsNull_doNotCallTransport() throws IOException {
        // Arrange
        Notification notification = createNotification(Notification.Event.AUTHORIZED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        ChannelModule<Notification> module = mock(ChannelModule.class);
        when(module.onSending(notification)).thenReturn(null);
        target.getNotificationModules().add(module);

        // Act
        target.sendNotification(notification);

        // Assert
        assertEquals(0, transport.sentEnvelopes.size());
    }

    @Test
    public void sendNotification_multipleRegisteredModules_callsEachModuleOnce() throws IOException {
        // Arrange
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Notification> module = mock(ChannelModule.class);
            when(module.onSending(notification)).thenReturn(notification);
            target.getNotificationModules().add(module);
        }

        // Act
        target.sendNotification(notification);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(notification, transport.sentEnvelopes.remove());
        for (ChannelModule<Notification> module : target.getNotificationModules()) {
            verify(module, times(1)).onSending(notification);
        }
    }

    @Test
    public void onReceiveNotification_registeredListenerTwoReceives_callsListenerAndUnregister() throws InterruptedException {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, true);

        // Act
        transport.raiseOnReceive(notification);
        transport.raiseOnReceive(notification);

        // Assert
        verify(listener, times(1)).onReceiveNotification(notification);
    }

    @Test
    public void onReceiveNotification_registeredListenersMultipleReceives_callsListenersMultipleTimes() throws InterruptedException {
        // Arrange
        int notificationCount = createRandomInt(100) + 1;
        int listenersCount = createRandomInt(10) + 1;

        // Arrange
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        List<NotificationChannel.NotificationChannelListener> listeners = new ArrayList<>();
        for (int i = 0; i < listenersCount; i++) {
            NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
            target.addNotificationListener(listener, false);
            listeners.add(listener);
        }

        // Act
        for (int i = 0; i < notificationCount; i++) {
            transport.raiseOnReceive(notification);
        }

        // Assert
        for (int i = 0; i < listenersCount; i++) {
            verify(listeners.get(i), times(notificationCount)).onReceiveNotification(notification);
        }
    }

    @Test
    public void onReceiveNotification_moduleReturnsNotification_receivesModuleNotification() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.AUTHORIZED);
        Notification moduleNotification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, true);
        ChannelModule<Notification> module = mock(ChannelModule.class);
        when(module.onReceiving(notification)).thenReturn(moduleNotification);
        target.getNotificationModules().add(module);

        // Act
        transport.raiseOnReceive(notification);

        // Assert
        verify(module, times(1)).onReceiving(notification);
        verify(listener, times(1)).onReceiveNotification(moduleNotification);
    }

    @Test
    public void onReceiveNotification_moduleReturnsNull_ignoresNotification()  {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification1 = createNotification(Notification.Event.AUTHORIZED);
        Notification notification2 = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, true);
        ChannelModule<Notification> module = mock(ChannelModule.class);
        when(module.onReceiving(notification1)).thenReturn(null);
        when(module.onReceiving(notification2)).thenReturn(notification2);
        target.getNotificationModules().add(module);

        // Act
        transport.raiseOnReceive(notification1);
        transport.raiseOnReceive(notification2);

        // Assert
        verify(module, times(1)).onReceiving(notification1);
        verify(module, times(1)).onReceiving(notification2);
        verify(listener, never()).onReceiveNotification(notification1);
        verify(listener, times(1)).onReceiveNotification(notification2);
    }

    @Test
    public void onReceiveNotification_multipleRegisteredModules_callsEachModuleOnce() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.AUTHORIZED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, true);
        int modulesCount = createRandomInt(10) + 1;
        for (int i = 0; i < modulesCount; i++) {
            ChannelModule<Notification> module = mock(ChannelModule.class);
            when(module.onReceiving(notification)).thenReturn(notification);
            target.getNotificationModules().add(module);
        }

        // Act
        transport.raiseOnReceive(notification);

        // Assert
        verify(listener, times(1)).onReceiveNotification(notification);
        for (ChannelModule<Notification> module : target.getNotificationModules()) {
            verify(module, times(1)).onReceiving(notification);
        }
    }

    @Test
    public void sendSession_establishedState_callsTransport() throws IOException {
        // Arrange
        Session session = createSession(Session.SessionState.ESTABLISHED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendSession(session);

        // Assert
        assertEquals(1, transport.sentEnvelopes.size());
        assertEquals(session, transport.sentEnvelopes.remove());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendSession_nullSession_throwsIllegalArgumentException() throws IOException {
        // Arrange
        Session session = null;
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        target.sendSession(session);
    }
    
    @Test
    public void onReceiveSession_registeredListenerTwoReceives_callsListenerAndUnregister() throws InterruptedException {
        // Arrange
        SessionChannel.SessionChannelListener listener = mock(SessionChannel.SessionChannelListener.class);
        Session session = createSession(Session.SessionState.ESTABLISHED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.enqueueSessionListener(listener);

        // Act
        transport.raiseOnReceive(session);
        transport.raiseOnReceive(session);

        // Assert
        verify(listener, times(1)).onReceiveSession(session);
    }
    
    @Test
    public void getTransport_anyInstance_returnsInstance() {
        // Arrange
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        Transport actual = target.getTransport();

        // Assert
        assertEquals(transport, actual);
    }

    @Test
    public void getRemoteNode_nullInstance_returnsNull() {
        // Arrange
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        Node actual = target.getRemoteNode();

        // Assert
        assertNull(actual);
    }

    @Test
    public void getRemoteNode_anyInstance_returnsInstance() {
        // Arrange
        Node remoteNode = createNode();
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, false, remoteNode, null);

        // Act
        Node actual = target.getRemoteNode();

        // Assert
        assertEquals(remoteNode, actual);
    }

    @Test
    public void getLocalNode_nullInstance_returnsNull() {
        // Arrange
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        Node actual = target.getLocalNode();

        // Assert
        assertNull(actual);
    }

    @Test
    public void getLocalNode_anyInstance_returnsInstance() {
        // Arrange
        Node localNode = createNode();
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, false, null, localNode);

        // Act
        Node actual = target.getLocalNode();

        // Assert
        assertEquals(localNode, actual);
    }

    @Test
    public void getSessionId_nullInstance_returnsNull() {
        // Arrange
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);

        // Act
        String actual = target.getSessionId();

        // Assert
        assertNull(actual);
    }

    @Test
    public void getSessionId_anyInstance_returnsInstance() {
        // Arrange
        String sessionId = EnvelopeId.newId();
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, false, null, null, sessionId);

        // Act
        String actual = target.getSessionId();

        // Assert
        assertEquals(sessionId, actual);
    }
    
    @Test
    public void getState_new_returnsInstance() {
        // Arrange
        Session.SessionState sessionState = Session.SessionState.NEW;
        ChannelBase target = getTarget(sessionState);
        
        // Act
        Session.SessionState actual = target.getState();
        
        // Assert
        assertEquals(sessionState, actual);
    }
/*
    @Test
    public void setState_negotiation_setsValueAndStartsRemovableListener() {
        // Arrange
        Session.SessionState state = Session.SessionState.NEGOTIATING;
        ChannelBase target = getTarget(Session.SessionState.NEW);

        // Act
        ((TestChannel)target).setState(state);

        // Assert
        assertEquals(state, target.getState());
        assertEquals(1, transport.addedListeners.size());
        TransportListenerRemoveAfterReceive listener = transport.addedListeners.remove();
        assertNotNull(listener.transportListener);
        assertEquals(true, listener.removeAfterReceive);
    }
*/
    @Test(expected = IllegalArgumentException.class)
    public void setState_null_throwsIllegalArgumentException() {
        // Arrange
        Session.SessionState state = null;
        ChannelBase target = getTarget(Session.SessionState.NEW);

        // Act
        ((TestChannel)target).setState(state);
    }

    @Test
    public void raiseOnReceiveMessage_registeredRemovableListener_callsListenerOnceAndRemove() {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, true);
        
        // Act
        ((TestChannel)target).raiseOnReceiveMessage(message);
        ((TestChannel)target).raiseOnReceiveMessage(message);

        // Assert
        verify(listener, times(1)).onReceiveMessage(message);
    }

    @Test
    public void raiseOnReceiveMessage_registeredListener_callsListenerTwice() {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener, false);

        // Act
        ((TestChannel)target).raiseOnReceiveMessage(message);
        ((TestChannel)target).raiseOnReceiveMessage(message);

        // Assert
        verify(listener, times(2)).onReceiveMessage(message);
    }

    @Test
    public void raiseOnReceiveMessage_twoRegisteredListeners_callsFirstOnceAndRemoveAndSecondTwice() {
        // Arrange
        MessageChannel.MessageChannelListener listener1 = mock(MessageChannel.MessageChannelListener.class);
        MessageChannel.MessageChannelListener listener2 = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addMessageListener(listener1, true);
        target.addMessageListener(listener2, false);

        // Act
        ((TestChannel)target).raiseOnReceiveMessage(message);
        ((TestChannel)target).raiseOnReceiveMessage(message);

        // Assert
        verify(listener1, times(1)).onReceiveMessage(message);
        verify(listener2, times(2)).onReceiveMessage(message);
    }
    
    @Test(expected = IllegalStateException.class)
    public void raiseOnReceiveMessage_finishedSessionSate_throwsIllegalOperationException() {
        // Arrange
        MessageChannel.MessageChannelListener listener = mock(MessageChannel.MessageChannelListener.class);
        Message message = createMessage(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.FINISHED);
        target.addMessageListener(listener, true);

        // Act
        ((TestChannel)target).raiseOnReceiveMessage(message);
    }

    @Test
    public void raiseOnReceiveCommand_registeredRemovableListener_callsListenerOnceAndRemove() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, true);

        // Act
        ((TestChannel)target).raiseOnReceiveCommand(command);
        ((TestChannel)target).raiseOnReceiveCommand(command);

        // Assert
        verify(listener, times(1)).onReceiveCommand(command);
    }

    @Test
    public void raiseOnReceiveCommand_registeredListener_callsListenerTwice() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener, false);

        // Act
        ((TestChannel)target).raiseOnReceiveCommand(command);
        ((TestChannel)target).raiseOnReceiveCommand(command);

        // Assert
        verify(listener, times(2)).onReceiveCommand(command);
    }

    @Test
    public void raiseOnReceiveCommand_twoRegisteredListeners_callsFirstOnceAndRemoveAndSecondTwice() {
        // Arrange
        CommandChannel.CommandChannelListener listener1 = mock(CommandChannel.CommandChannelListener.class);
        CommandChannel.CommandChannelListener listener2 = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addCommandListener(listener1, true);
        target.addCommandListener(listener2, false);

        // Act
        ((TestChannel)target).raiseOnReceiveCommand(command);
        ((TestChannel)target).raiseOnReceiveCommand(command);

        // Assert
        verify(listener1, times(1)).onReceiveCommand(command);
        verify(listener2, times(2)).onReceiveCommand(command);
    }

    @Test(expected = IllegalStateException.class)
    public void raiseOnReceiveCommand_finishedSessionSate_throwsIllegalOperationException() {
        // Arrange
        CommandChannel.CommandChannelListener listener = mock(CommandChannel.CommandChannelListener.class);
        Command command = createCommand(createPlainDocument());
        ChannelBase target = getTarget(Session.SessionState.FINISHED);
        target.addCommandListener(listener, true);

        // Act
        ((TestChannel)target).raiseOnReceiveCommand(command);
    }

    @Test
    public void raiseOnReceiveNotification_registeredRemovableListener_callsListenerOnceAndRemove() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, true);

        // Act
        ((TestChannel)target).raiseOnReceiveNotification(notification);
        ((TestChannel)target).raiseOnReceiveNotification(notification);

        // Assert
        verify(listener, times(1)).onReceiveNotification(notification);
    }

    @Test
    public void raiseOnReceiveNotification_registeredListener_callsListenerTwice() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener, false);

        // Act
        ((TestChannel)target).raiseOnReceiveNotification(notification);
        ((TestChannel)target).raiseOnReceiveNotification(notification);

        // Assert
        verify(listener, times(2)).onReceiveNotification(notification);
    }

    @Test
    public void raiseOnReceiveNotification_twoRegisteredListeners_callsFirstOnceAndRemoveAndSecondTwice() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener1 = mock(NotificationChannel.NotificationChannelListener.class);
        NotificationChannel.NotificationChannelListener listener2 = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED);
        target.addNotificationListener(listener1, true);
        target.addNotificationListener(listener2, false);

        // Act
        ((TestChannel)target).raiseOnReceiveNotification(notification);
        ((TestChannel)target).raiseOnReceiveNotification(notification);

        // Assert
        verify(listener1, times(1)).onReceiveNotification(notification);
        verify(listener2, times(2)).onReceiveNotification(notification);
    }

    @Test(expected = IllegalStateException.class)
    public void raiseOnReceiveNotification_finishedSessionSate_throwsIllegalOperationException() {
        // Arrange
        NotificationChannel.NotificationChannelListener listener = mock(NotificationChannel.NotificationChannelListener.class);
        Notification notification = createNotification(Notification.Event.RECEIVED);
        ChannelBase target = getTarget(Session.SessionState.FINISHED);
        target.addNotificationListener(listener, true);

        // Act
        ((TestChannel)target).raiseOnReceiveNotification(notification);
    }

    @Test
    public void schedulePing_inactiveEstablishedChannel_sendPings() throws InterruptedException {
        // Arrange
        ChannelBase target = getTarget(Session.SessionState.ESTABLISHED, false, true, 100, 600, null, null, EnvelopeId.newId());

        // Act
        Thread.sleep(350);

        // Assert
        assertEquals(3, ((TestTransport) target.getTransport()).sentEnvelopes.size());
    }

    private class TestChannel extends ChannelBase {
        protected TestChannel(Transport transport, Session.SessionState state, boolean fillEnvelopeRecipients, boolean autoReplyPings, long pingInterval, long pingDisconnectionInterval, Node remoteNode, Node localNode, String sessionId) {
            super(transport, fillEnvelopeRecipients, autoReplyPings, pingInterval, pingDisconnectionInterval, new ChannelCommandProcessorImpl());
            setRemoteNode(remoteNode);
            setLocalNode(localNode);
            setState(state);
            setSessionId(sessionId);
        }

        public Session lastReceivedSession;

        @Override
        protected synchronized void raiseOnReceiveSession(Session session) {
            lastReceivedSession = session;
            super.raiseOnReceiveSession(session);
        }
    }

    private class TestTransport extends TransportBase implements Transport {
        public URI openUri;
        public Queue<Envelope> sentEnvelopes;
        public boolean closeInvoked;

        public TestTransport() {
            sentEnvelopes = new LinkedBlockingQueue<>();
        }

        /**
         * Closes the transport.
         */
        @Override
        protected void performClose() throws IOException {
            closeInvoked = true;
        }

        @Override
        protected void performOpen(URI uri) throws IOException {

        }

        /**
         * Sends an envelope to the remote node.
         *
         * @param envelope
         */
        @Override
        public void send(Envelope envelope) throws IOException {
            sentEnvelopes.add(envelope);
        }

        /**
         * Opens the transport connection with the specified Uri.
         *
         * @param uri
         */
        @Override
        public void open(URI uri) throws IOException {
            openUri = uri;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void setStateListener(TransportStateListener listener) {
            super.setStateListener(listener);
        }
    }
}