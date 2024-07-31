package com.apollo.backendapi;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.rpl.rama.integration.ExternalDepot;
import com.rpl.rama.integration.TaskGlobalContext;

public class AbiosExternalDepot implements ExternalDepot {

    @Override
    public void prepareForTask(int taskId, TaskGlobalContext context) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareForTask'");
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public CompletableFuture<Long> endOffset(int partitionIndex) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'endOffset'");
    }

    @Override
    public CompletableFuture<List> fetchFrom(int partitionIndex, long startOffset) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fetchFrom'");
    }

    @Override
    public CompletableFuture<List> fetchFrom(int partitionIndex, long startOffset, long endOffset) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fetchFrom'");
    }

    @Override
    public CompletableFuture<Integer> getNumPartitions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNumPartitions'");
    }

    @Override
    public CompletableFuture<Long> offsetAfterTimestampMillis(int partitionIndex, long millis) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'offsetAfterTimestampMillis'");
    }

    @Override
    public CompletableFuture<Long> startOffset(int partitionIndex) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'startOffset'");
    }
    
}
