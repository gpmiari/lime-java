package org.limeprotocol.messaging.resource;

import org.limeprotocol.Document;
import org.limeprotocol.Identity;
import org.limeprotocol.MediaType;
import org.limeprotocol.util.StringUtils;

public class Contact implements Document {

    public static final String MIME_TYPE = "application/vnd.lime.contact+json";
    private MediaType mediaType;

    public Contact() {
        this.mediaType = MediaType.parse(MIME_TYPE);
    }

    /**
     * The identity of the contact,
     * in the name@domain format.
     */
    private Identity identity;

    /**
     * The name of the contact.
     * This information is only visible by the roster owner.
     */
    private String name;

    /**
     * Determines if the contact is pending for
     * acceptance by the roster owner.
     * The default value is false.
     */
    private boolean isPending;

    /**
     * Indicates if the roster owner wants to share
     * presence information with the contact. If true,
     * the server provides a get delegation permission
     * to the contact identity into the roster owner
     * presence resource. The default value is true.
     */
    private boolean sharePresence;

    /**
     * Indicates if the roster owner wants to share account
     * information with the contact. If true, the server provides
     * a get delegation permission to the contact identity
     * into the roster owner account resource.
     * The default value is true.
     */
    private boolean shareAccountInfo;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public boolean isPending() {
        return isPending;
    }

    public void setIsPending(Boolean isPending) {
        this.isPending = isPending;
    }

    public boolean getShareAccountInfo() {
        return shareAccountInfo;
    }

    public void setShareAccountInfo(Boolean shareAccountInfo) {
        this.shareAccountInfo = shareAccountInfo;
    }

    public boolean getSharePresence() {
        return sharePresence;
    }

    public void setSharePresence(Boolean sharePresence) {
        this.sharePresence = sharePresence;
    }

    /**
     *Returns a <see cref="System.String" /> that represents this instance.
     */
    @Override
    public String toString() {
        return this.identity != null ? this.identity.toString() : StringUtils.STRING_EMPTY;
    }

    /**
     *Determines whether the specified <see cref="System.Object" }, is equal to this instance.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        return this.toString().equalsIgnoreCase(obj.toString());
    }

    /**
     *Returns a hash code for this instance.
     */
    @Override
    public int hashCode() {
        return this.identity != null ? this.identity.hashCode() : 0;
    }

    @Override
    public MediaType getMediaType() {
        return this.mediaType;
    }
}
