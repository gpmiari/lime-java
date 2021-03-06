package org.limeprotocol.messaging.resources;

import org.limeprotocol.*;

import static org.limeprotocol.Command.*;

/**
 * Represents a delegation to send envelopes on behalf of another
 * identity of the same network. The delegation can be constrained to
 * specific envelope types and/or destinations.
 * It is associated to the issuer's session and can
 * be revoked through a delete command.
 */

public class Delegation extends DocumentBase {

    public static final String MIME_TYPE = "application/vnd.lime.delegation+json";

    public Delegation() {
        super(MediaType.parse(MIME_TYPE));
    }

    private Node target;

    /**
     * Array of destinations that the delegated
     * identity can originate envelopes on behalf.
     */
    private Identity[] destinations;

    /**
     * Command definitions for delegation.
     */
    private DelegationCommand[] commands;

    /**
     * Message definitions for delegation.
     */
    private MediaType[] messages;


    public DelegationCommand[] getCommands() {
        return commands;
    }

    public void setCommands(DelegationCommand[] commands) {
        this.commands = commands;
    }

    public Identity[] getDestinations() {
        return destinations;
    }

    public void setDestinations(Identity[] destinations) {
        this.destinations = destinations;
    }

    public MediaType[] getMessages() {
        return messages;
    }

    public void setMessages(MediaType[] messages) {
        messages = messages;
    }

    public Node getTarget() {
        return target;
    }

    public void setTarget(Node target) {
        this.target = target;
    }

    public class DelegationCommand {
        public final String TYPE_KEY = "type";
        public final String METHODS_KEY = "methods";

        private MediaType type;

        private CommandMethod methods;

        public MediaType getType() {
            return type;
        }

        public void setType(MediaType type) {
            this.type = type;
        }

        public CommandMethod getMethods() {
            return methods;
        }

        public void setMethods(CommandMethod methods) {
            this.methods = methods;
        }
    }
}
