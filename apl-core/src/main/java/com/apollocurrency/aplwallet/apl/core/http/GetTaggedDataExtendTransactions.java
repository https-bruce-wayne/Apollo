/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.http;

import com.apollocurrency.aplwallet.apl.core.app.Appendix;
import com.apollocurrency.aplwallet.apl.core.app.Attachment;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.AplCore;
import com.apollocurrency.aplwallet.apl.util.AplException;
import com.apollocurrency.aplwallet.apl.core.app.TaggedData;
import com.apollocurrency.aplwallet.apl.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetTaggedDataExtendTransactions extends APIServlet.APIRequestHandler {

    private static class GetTaggedDataExtendTransactionsHolder {
        private static final GetTaggedDataExtendTransactions INSTANCE = new GetTaggedDataExtendTransactions();
    }

    public static GetTaggedDataExtendTransactions getInstance() {
        return GetTaggedDataExtendTransactionsHolder.INSTANCE;
    }

    private GetTaggedDataExtendTransactions() {
        super(new APITag[] {APITag.DATA}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws AplException {
        long taggedDataId = ParameterParser.getUnsignedLong(req, "transaction", true);
        List<Long> extendTransactions = TaggedData.getExtendTransactionIds(taggedDataId);
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        Blockchain blockchain = AplCore.getBlockchain();
        Filter<Appendix> filter = (appendix) -> ! (appendix instanceof Attachment.TaggedDataExtend);
        extendTransactions.forEach(transactionId -> jsonArray.add(JSONData.transaction(blockchain.getTransaction(transactionId), filter, false)));
        response.put("extendTransactions", jsonArray);
        return response;
    }

}