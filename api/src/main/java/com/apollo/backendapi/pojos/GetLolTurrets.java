package com.apollo.backendapi.pojos;

import com.apollo.backend.data.LolTurrets;

public class GetLolTurrets {
    public GetLolTurret topOuter;
    public GetLolTurret topInner;
    public GetLolTurret topInhibitor;
    public GetLolTurret topNexus;
    public GetLolTurret midOuter;
    public GetLolTurret midInner;
    public GetLolTurret midInhibitor;
    public GetLolTurret botOuter;
    public GetLolTurret botInner;
    public GetLolTurret botInhibitor;
    public GetLolTurret botNexus;

    public GetLolTurrets(LolTurrets turrets) {
        this.topOuter = new GetLolTurret(turrets.getTopOuter());
        this.topInner = new GetLolTurret(turrets.getTopInner());
        this.topInhibitor = new GetLolTurret(turrets.getTopInhibitor());
        this.topNexus = new GetLolTurret(turrets.getTopNexus());
        this.midOuter = new GetLolTurret(turrets.getMidOuter());
        this.midInner = new GetLolTurret(turrets.getMidInner());
        this.midInhibitor = new GetLolTurret(turrets.getMidInhibitor());
        this.botOuter = new GetLolTurret(turrets.getBotOuter());
        this.botInner = new GetLolTurret(turrets.getBotInner());
        this.botInhibitor = new GetLolTurret(turrets.getBotInhibitor());
        this.botNexus = new GetLolTurret(turrets.getBotNexus());
    }
}