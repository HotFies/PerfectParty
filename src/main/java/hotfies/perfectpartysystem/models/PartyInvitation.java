package hotfies.perfectpartysystem.models;

import java.util.UUID;

public class PartyInvitation {
    private final UUID inviterUuid;
    private final UUID inviteeUuid;
    private final String inviteeName;
    private final long timestamp;

    public PartyInvitation(UUID inviterUuid, UUID inviteeUuid, String inviteeName) {
        this.inviterUuid = inviterUuid;
        this.inviteeUuid = inviteeUuid;
        this.inviteeName = inviteeName;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public UUID getInviteeUuid() {
        return inviteeUuid;
    }

    public String getInviteeName() {
        return inviteeName;
    }

    public long getTimestamp() {
        return timestamp;
    }
}