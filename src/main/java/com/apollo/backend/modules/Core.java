package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.helpers.ModuleUniqueIdPState;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.Ops;
import static com.rpl.rama.helpers.TopologyUtils.extractJavaFields;

import com.apollo.backend.pojos.PostAccount;

public class Core implements RamaModule {

    /**
     * Declares the accounts topology, handling unique IDs and existing registrations.
     * @param topologies The topologies object used to declare the stream topology.
     */
    private static void declareAccountsTopology(Topologies topologies) {
        StreamTopology accounts = topologies.stream("accounts");

        // Unique ID generator for accounts, used to ensure each account has a unique ID.
        ModuleUniqueIdPState accountIdGen = new ModuleUniqueIdPState("$$accountIdGen");
        accountIdGen.declarePState(accounts);

        // Persistent state mapping account names to IDs and UUIDs.
        // This helps in quickly checking if an account name already exists.
        accounts.pstate("$$nameToUser", PState.mapSchema(String.class,
                                                         PState.fixedKeysSchema("accountId", Long.class,
                                                                                "uuid", String.class)));

        // Persistent state to store account details.
        accounts.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, PostAccount.class));


        // Start a data stream from the "*accountDepot". This is the source of account registration data.
accounts.source("*accountDepot").out("*registration")
// Use a macro to extract specific fields from the registration data.
// This macro simplifies the process of accessing fields within the registration objects.
.macro(extractJavaFields("*registration", "*accountId", "*email", "*displayName", "*pwdHash", "*registrationUUID"))

// Add the current time in milliseconds to each registration. This is likely used as the account creation time.
.each(System::currentTimeMillis).out("*joinedAtMillis")

// Process the registration data to store in the $$accountDetails persistent state.
// This block checks if the account ID is null, which indicates a new registration.
.localTransform("$$accountDetails",
    Path.key("*accountId")
        .filterPred(Ops.IS_NULL)  // Filter to process only new registrations (where accountId is null).
        .multiPath(
            // For each new registration, set various account details.
            // These are likely fields in a map or similar data structure within the $$accountDetails state.
            Path.key("email").termVal("*email"),               // Set the email.
            Path.key("displayName").termVal("*displayName"),   // Set the display name.
            Path.key("pwdHash").termVal("*pwdHash"),           // Set the hashed password.
            Path.key("joinedAtMillis").termVal("*joinedAtMillis"), // Set the join date in milliseconds.
            Path.key("registrationUUID").termVal("*registrationUUID") // Set the registration UUID.
        ));

    }

    /**
     * Extracts 'accountId' field from Java objects.
     * This is used to hash account depots by account ID.
     */
    public static class AccountIdExtract extends TopologyUtils.ExtractJavaField {
        public AccountIdExtract() {
            super("accountId");
        }
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        // Declaring a depot hashed by account ID for efficient processing of account-related data.
        setup.declareDepot("*accountDepot", Depot.hashBy(AccountIdExtract.class));
        declareAccountsTopology(topologies);
        // Additional topologies can be declared here as needed.
    }
}
