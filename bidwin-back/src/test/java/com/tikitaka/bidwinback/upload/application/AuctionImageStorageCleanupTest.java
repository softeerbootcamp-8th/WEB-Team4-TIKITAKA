package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectDeletionResult;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuctionImageStorageCleanupTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션이_커밋되면_임시_객체를_삭제한다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageStorageCleanup cleanup = new AuctionImageStorageCleanup(objectStorage);
        List<String> temporaryKeys = List.of("temp/first", "temp/second");
        List<String> promotedKeys = new java.util.ArrayList<>();
        when(objectStorage.deleteAll(temporaryKeys))
                .thenReturn(new ObjectDeletionResult(temporaryKeys, List.of()));
        TransactionSynchronizationManager.initSynchronization();

        cleanup.register(temporaryKeys, promotedKeys);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(objectStorage).deleteAll(temporaryKeys);
        verifyNoMoreInteractions(objectStorage);
    }

    @Test
    void 트랜잭션이_롤백되면_이미_승격된_객체만_삭제한다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageStorageCleanup cleanup = new AuctionImageStorageCleanup(objectStorage);
        List<String> temporaryKeys = List.of("temp/first");
        List<String> promotedKeys = new java.util.ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        cleanup.register(temporaryKeys, promotedKeys);
        promotedKeys.add("auction-images/100/first.jpg");
        when(objectStorage.deleteAll(promotedKeys))
                .thenReturn(new ObjectDeletionResult(promotedKeys, List.of()));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(objectStorage).deleteAll(promotedKeys);
        verifyNoMoreInteractions(objectStorage);
    }

    @Test
    void 트랜잭션_완료_상태를_알_수_없으면_객체를_삭제하지_않는다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageStorageCleanup cleanup = new AuctionImageStorageCleanup(objectStorage);
        List<String> temporaryKeys = List.of("temp/first");
        List<String> promotedKeys = new java.util.ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        cleanup.register(temporaryKeys, promotedKeys);
        promotedKeys.add("auction-images/100/first.jpg");
        complete(TransactionSynchronization.STATUS_UNKNOWN);

        verifyNoMoreInteractions(objectStorage);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
