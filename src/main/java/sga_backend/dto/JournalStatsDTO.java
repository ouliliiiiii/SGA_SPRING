package sga_backend.dto;

public class JournalStatsDTO {
    private long connexionsAujourdhui;
    private long actionsAujourdhui;
    private long actionsSemaine;
    private long total;

    public long getConnexionsAujourdhui() { return connexionsAujourdhui; }
    public void setConnexionsAujourdhui(long v) { this.connexionsAujourdhui = v; }

    public long getActionsAujourdhui() { return actionsAujourdhui; }
    public void setActionsAujourdhui(long v) { this.actionsAujourdhui = v; }

    public long getActionsSemaine() { return actionsSemaine; }
    public void setActionsSemaine(long v) { this.actionsSemaine = v; }

    public long getTotal() { return total; }
    public void setTotal(long v) { this.total = v; }
}