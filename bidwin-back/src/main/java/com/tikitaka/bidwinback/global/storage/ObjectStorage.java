package com.tikitaka.bidwinback.global.storage;

import java.util.List;

public interface ObjectStorage {

    /**
     * contentType과 contentLength를 서명 조건에 포함한다.
     * 클라이언트는 반환된 URL에 동일한 조건으로 업로드해야 한다.
     */
    PresignedUpload presignPut(
            String objectKey,
            String contentType,
            long contentLength
    );

    boolean exists(String objectKey);

    ObjectDeletionResult deleteAll(List<String> objectKeys);
}
