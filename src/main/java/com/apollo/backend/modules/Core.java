package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.helpers.ModuleUniqueIdPState;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.Ops;
import static com.rpl.rama.helpers.TopologyUtils.extractJavaFields;

import com.apollo.backend.data.*;

public class Core implements RamaModule {

    // TODO: Add logic for handling unique IDs and existing registrations
    private static void declareAccountsTopology(Topologies topologies) {
        StreamTopology accounts = topologies.stream("accounts");

        // Unique ID generator for accounts
        ModuleUniqueIdPState accountIdGen = new ModuleUniqueIdPState("$$accountIdGen");
        accountIdGen.declarePState(accounts);

        // Persistent state to map account names to IDs and UUIDs
        accounts.pstate("$$nameToUser", PState.mapSchema(String.class,
                                                         PState.fixedKeysSchema("accountId", Long.class,
                                                                                "uuid", String.class)));

        // Persistent state to store account details
        accounts.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, Account.class));


        accounts.source("*accountDepot").out("*registration")
                .macro(extractJavaFields("*registration", "*accountId", "*email", "*displayName", "*pwdHash", "*registrationUUID"))
                .each(System::currentTimeMillis).out("*joinedAtMillis")
                .localTransform("$$accountDetails",
                    Path.key("*accountId")
                        .filterPred(Ops.IS_NULL)
                        .multiPath(Path.key("email").termVal("*email"),
                                   Path.key("displayName").termVal("*displayName"),
                                   Path.key("pwdHash").termVal("*pwdHash"),
                                   Path.key("joinedAtMillis").termVal("*joinedAtMillis"),
                                   Path.key("registrationUUID").termVal("*registrationUUID")));
    }

    public static class AccountIdExtract extends TopologyUtils.ExtractJavaField {
        public AccountIdExtract() {
            super("accountId");
        }
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.declareDepot("*accountDepot", Depot.hashBy(AccountIdExtract.class));
        declareAccountsTopology(topologies);
        // Additional topologies can be declared here
    }
}
