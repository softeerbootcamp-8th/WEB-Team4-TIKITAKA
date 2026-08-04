package com.tikitaka.bidwinback.upload.application;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuctionImageDraftService {

    public UUID issue() {
        return UUID.randomUUID();
    }
}
