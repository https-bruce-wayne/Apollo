package com.apollocurrency.aplwallet.api.dto;

public enum  EventType {
    BLOCK_GENERATED, ORDINARY_PAYMENT, TRANSACTION_FEE,
    REJECT_PHASED_TRANSACTION,ASSET_ISSUANCE,ALIAS_BUY,
    ASSET_ASK_ORDER_PLACEMENT,ASSET_BID_ORDER_PLACEMENT,
    ASSET_ASK_ORDER_CANCELLATION,ASSET_BID_ORDER_CANCELLATION,
    ASSET_DELETE,ASSET_TRANSFER,DEX_FREEZE_MONEY,DEX_TRANSFER_MONEY,DEX_REFUND_FROZEN_MONEY,
    SHUFFLING_CANCELLATION,SHUFFLING_REGISTRATION,CURRENCY_ISSUANCE,
    CURRENCY_PUBLISH_EXCHANGE_OFFER,CURRENCY_TRANSFER
}
