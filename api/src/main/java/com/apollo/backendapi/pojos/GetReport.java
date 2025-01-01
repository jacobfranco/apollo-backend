package com.apollo.backendapi.pojos;

import java.util.*;

import com.apollo.backend.ApolloHelpers;
import com.apollo.backend.data.Report;

public class GetReport {
    public String id;
    public String state;
    public boolean action_taken;
    public String action_taken_at;
    public String category;
    public String comment;
    public boolean forwarded;
    public String created_at;
    public List<String> status_ids;
    public List<String> rule_ids;
    // Key changes below:
    public Map<String, Object> target_account; // Change from GetAccount to Map
    public Map<String, Object> account; // Change from GetAccount to Map
    public List<Map<String, Object>> statuses; // Change from GetStatus to Map

    public GetReport(Report report) {
        this.id = report.getId();
        this.state = report.getState();
        this.action_taken = report.isAction_taken();
        this.action_taken_at = report.getAction_taken_at();
        this.category = report.getCategory();
        this.comment = report.getComment();
        this.forwarded = report.isForwarded();
        this.created_at = report.getCreated_at();
        this.status_ids = report.getStatus_ids();
        this.rule_ids = report.getRule_ids();

        if (report.getTarget_account_id() != 0) {
            Map<String, Object> targetAccount = new HashMap<>();
            targetAccount.put("account", new HashMap<String, Object>() {
                {
                    put("id", ApolloHelpers.serializeAccountId(report.getTarget_account_id()));
                }
            });
            this.target_account = targetAccount;
        }

        if (report.getReporter_account_id() != 0) {
            Map<String, Object> reporterAccount = new HashMap<>();
            reporterAccount.put("account", new HashMap<String, Object>() {
                {
                    put("id", ApolloHelpers.serializeAccountId(report.getReporter_account_id()));
                }
            });
            this.account = reporterAccount;
        }
    }
}