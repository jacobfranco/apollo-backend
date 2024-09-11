package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Coverage;

public class GetCoverage {
    public GetCoverageData data;

    public GetCoverage(Coverage coverage) {
        this.data = coverage.isSetData() ? new GetCoverageData(coverage.getData()) : null;
    }

    public static class GetCoverageData {
        public GetCoverageType live;
        public GetCoverageType realtime;
        public GetCoverageType postgame;

        public GetCoverageData(com.apollo.backend.data.CoverageData cd) {
            this.live = cd.isSetLive() ? new GetCoverageType(cd.getLive()) : null;
            this.realtime = cd.isSetRealtime() ? new GetCoverageType(cd.getRealtime()) : null;
            this.postgame = cd.isSetPostgame() ? new GetCoverageType(cd.getPostgame()) : null;
        }
    }

    public static class GetCoverageType {
        public GetCoverageStatus api;
        public GetCoverageStatus cv;
        public GetCoverageStatus server;

        public GetCoverageType(com.apollo.backend.data.CoverageType ct) {
            this.api = ct.isSetApi() ? new GetCoverageStatus(ct.getApi()) : null;
            this.cv = ct.isSetCv() ? new GetCoverageStatus(ct.getCv()) : null;
            this.server = ct.isSetServer() ? new GetCoverageStatus(ct.getServer()) : null;
        }
    }

    public static class GetCoverageStatus {
        public String expectation;
        public String fact;

        public GetCoverageStatus(com.apollo.backend.data.CoverageStatus cs) {
            this.expectation = cs.getExpectation();
            this.fact = cs.getFact();
        }
    }
}