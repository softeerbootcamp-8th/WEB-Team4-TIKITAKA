package com.tikitaka.bidwinback.auction.domain.enums;

public enum AuctionCategory{
    HOUSEHOLD("생활용품"),
    FOOD("먹거리"),
    FURNITURE("가구");

    private final String label;

    AuctionCategory(String label){
        this.label = label;
    }

    public String getLabel(){
        return this.label;
    }
}