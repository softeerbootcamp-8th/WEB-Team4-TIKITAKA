package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectDeletionResult;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionImageStorageCleanup {

    private final ObjectStorage objectStorage;

    public void register(
            List<String> temporaryObjectKeys,
            List<String> promotedObjectKeys
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("경매 이미지 정리는 활성 트랜잭션 안에서 등록해야 합니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        // 커밋 후에는 임시 객체를, 롤백 후에는 복사된 영구 객체를 제거해 양쪽 저장소를 맞춘다.
                        if (status == STATUS_COMMITTED) {
                            delete(temporaryObjectKeys, "임시");
                            return;
                        }
                        if (status == STATUS_ROLLED_BACK) {
                            delete(List.copyOf(promotedObjectKeys), "승격");
                            return;
                        }
                        // 결과가 불명확할 때 영구 객체를 지우면 실제로 커밋된 경매의 이미지가 유실될 수 있다.
                        log.atWarn()
                                .addKeyValue("event", "auction_image_cleanup_skipped_unknown_transaction_status")
                                .addKeyValue("status", status)
                                .log("경매 이미지 정리를 생략합니다: 알 수 없는 트랜잭션 완료 상태");
                    }
                }
        );
    }

    private void delete(List<String> objectKeys, String objectType) {
        if (objectKeys.isEmpty()) {
            return;
        }
        try {
            ObjectDeletionResult result = objectStorage.deleteAll(objectKeys);
            result.failures().forEach(failure -> log.atWarn()
                    .addKeyValue("event", "auction_image_cleanup_delete_failed")
                    .addKeyValue("objectType", objectType)
                    .addKeyValue("objectKey", failure.objectKey())
                    .addKeyValue("failureCode", failure.code())
                    .log("경매 이미지 삭제 실패"));
        } catch (RuntimeException exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("event", "auction_image_cleanup_failed")
                    .addKeyValue("objectType", objectType)
                    .log("경매 이미지 삭제 중 오류가 발생했습니다.");
        }
    }
}
