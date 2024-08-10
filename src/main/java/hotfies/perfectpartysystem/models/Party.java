package hotfies.perfectpartysystem.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Party {
    private UUID leaderUuid;
    private String leaderName;
    private final List<UUID> memberUuids;
    private final List<String> memberNames;

    public Party(UUID leaderUuid, String leaderName) {
        this.leaderUuid = leaderUuid;
        this.leaderName = leaderName;
        this.memberUuids = new ArrayList<>();
        this.memberNames = new ArrayList<>();
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeader(UUID leaderUuid, String leaderName) {
        this.leaderUuid = leaderUuid;
        this.leaderName = leaderName;
    }

    public List<UUID> getMemberUuids() {
        return memberUuids;
    }

    public List<String> getMemberNames() {
        return memberNames;
    }

    public void addMember(UUID memberUuid, String memberName) {
        memberUuids.add(memberUuid);
        memberNames.add(memberName);
    }

    public void removeMember(UUID memberUuid) {
        int index = memberUuids.indexOf(memberUuid);
        if (index != -1) {
            memberUuids.remove(index);
            memberNames.remove(index);
        }
    }

    public List<UUID> getAllMemberUuidsIncludingLeader() {
        List<UUID> allMembers = new ArrayList<>(memberUuids);
        allMembers.add(leaderUuid);
        return allMembers;
    }
}