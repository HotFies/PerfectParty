package hotfies.perfectpartysystem.models;

import net.md_5.bungee.api.scheduler.ScheduledTask;

public class PlayerState {
    private boolean isReconnecting;
    private ScheduledTask reconnectTask;

    public PlayerState() {
        this.isReconnecting = false;
        this.reconnectTask = null;
    }

    public boolean isReconnecting() {
        return isReconnecting;
    }

    public void setReconnecting(boolean reconnecting) {
        isReconnecting = reconnecting;
    }

    public ScheduledTask getReconnectTask() {
        return reconnectTask;
    }

    public void setReconnectTask(ScheduledTask reconnectTask) {
        this.reconnectTask = reconnectTask;
    }
}