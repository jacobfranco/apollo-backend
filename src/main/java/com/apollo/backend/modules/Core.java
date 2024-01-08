package com.apollo.backend.modules;

import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import com.apollo.backend.*;
import com.apollo.backend.data.*;

import static com.apollo.backend.ApolloHelpers.extractFields;

public class Core implements RamaModule {

    private static void declareAccountsTopology(Topologies topologies) {
      StreamTopology stream = topologies.stream("accounts");
      ModuleUniqueIdPState accountIdGen = new ModuleUniqueIdPState("$$accountIdGen");
      accountIdGen.declarePState(stream);
      stream.pstate("$$nameToUser", PState.mapSchema(String.class,
                                                     PState.fixedKeysSchema("accountId", Long.class,
                                                                            "uuid", String.class)));
      stream.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, Account.class));

      /*
        User registration does three things when that name is not already registered:
          - generates a user id for that user
          - updates $$nameToUser PState (which contains a mapping from name -> user id)
          - updates $$accountIdToAccount PState (which maps user id to Account)

        User registration is implemented to correctly handle:
          - Concurrent registration of same name (first one wins)
          - Failures of topology (e.g. a machine involved in the processing dies midway through
            processing). Streaming failures are handled by retrying from the start of the topology.
       */
      stream.source("*accountDepot").out("*data")
            .macro(extractFields("*data", "*name", "*uuid"))
            .localSelect("$$nameToUser", Path.key("*name")).out("*currInfo")
            .each(Ops.GET, "*currInfo", "uuid").out("*currUUID")
            // By including a UUID with each registration request, we can distinguish between:
            //   - this name is already registered by a different request so we shouldn't override it
            //   - this name was registered by the same request, so we should continue finishing the
            //     registration
            .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, "*currInfo"),
                                     new Expr(Ops.EQUAL, "*uuid", "*currUUID")),
              Block.macro(accountIdGen.genId("*accountId"))
                   .localTransform("$$nameToUser", Path.key("*name").multiPath(Path.key("accountId").termVal("*accountId"),
                                                                               Path.key("uuid").termVal("*uuid")))
                   .hashPartition("*accountId")
                   .localTransform("$$accountIdToAccount", Path.key("*accountId").termVal("*data"))
                   .invokeQuery("getAccountMetadata", null, "*accountId").out("*metadata")
                   .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId", "*data", "*metadata").out("*accountWithId")
                   .depotPartitionAppend("*accountWithIdDepot", "*accountWithId"));

        /*  TODO: Implement Account Edits
      stream.source("*accountEditDepot", StreamSourceOptions.retryNone()).out("*editAccount")
            .macro(extractFields("*editAccount", "*accountId", "*edits"))
            .each(Ops.EXPLODE, "*edits").out("*edit")
            .each((EditAccountField editAccount, OutputCollector collector) -> {
                collector.emit(editAccount.getSetField().getFieldName(), editAccount.getFieldValue());
            }, "*edit").out("*fieldName", "*fieldValue")
            .localTransform("$$accountIdToAccount", Path.must("*accountId")
                                                        .customNavBuilder(TField::new, "*fieldName")
                                                        .termVal("*fieldValue"));
                                                        */
  }

    @Override
    public void define(Setup setup, Topologies topologies) {
        // Declaring a depot hashed by account ID for efficient processing of account-related data.
        setup.declareDepot("*accountDepot", Depot.hashBy(ApolloHelpers.ExtractName.class));
        declareAccountsTopology(topologies);
        // Additional topologies can be declared here as needed.
    }
}
