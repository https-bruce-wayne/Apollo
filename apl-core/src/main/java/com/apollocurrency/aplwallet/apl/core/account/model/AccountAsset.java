/*
 * Copyright © 2018-2019 Apollo Foundation
 */
package com.apollocurrency.aplwallet.apl.core.account.model;

import com.apollocurrency.aplwallet.apl.core.db.DbKey;
import com.apollocurrency.aplwallet.apl.core.db.model.VersionedDerivedEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author al
 */
@ToString(callSuper = true)
@Getter @Setter
public final class AccountAsset extends VersionedDerivedEntity {

    private final long accountId;
    private final long assetId;
    private long quantityATU;
    private long unconfirmedQuantityATU;


    public AccountAsset(long accountId, long assetId, long quantityATU, long unconfirmedQuantityATU, int height) {
        super(null, height);
        this.accountId = accountId;
        this.assetId = assetId;
        this.quantityATU = quantityATU;
        this.unconfirmedQuantityATU = unconfirmedQuantityATU;
    }

    public AccountAsset(ResultSet rs, DbKey dbKey) throws SQLException {
        super(rs);
        this.accountId = rs.getLong("account_id");
        this.assetId = rs.getLong("asset_id");
        this.quantityATU = rs.getLong("quantity");
        this.unconfirmedQuantityATU = rs.getLong("unconfirmed_quantity");
        setDbKey(dbKey);
    }

}
