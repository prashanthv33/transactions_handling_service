package com.fde.txn;

import com.fde.txn.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionControllerTest extends IntegrationTestBase {

    private static final String TXN = """
            {"transactionId":"TXN-1","requestId":"REQ-1","accountId":"ACC-1","transactionType":"CREDIT",
             "amount":100,"currency":"USD","sequenceNumber":1}""";

    @Autowired
    MockMvc mvc;

    @Test
    void submitIsAcceptedThenDuplicate() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(TXN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(TXN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }

    @Test
    void conflictingPayloadReturns409() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(TXN));
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(TXN.replace("\"amount\":100", "\"amount\":999")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("CONFLICT"));
    }

    @Test
    void invalidRecordReturns400WithReason() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(TXN.replace("\"amount\":100", "\"amount\":-10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("amount must be greater than zero"));
    }

    @Test
    void malformedJsonGetsASafeError() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void batchSubmitIsolatesBadRecordsAndStateIsQueryable() throws Exception {
        String batch = "[" + TXN + "," + TXN.replace("TXN-1", "TXN-2").replace("\"amount\":100", "\"amount\":\"x\"")
                + "]";
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$[0].outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].outcome").value("REJECTED"));

        processor.drain();

        mvc.perform(get("/transactions/TXN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.status").value("PROCESSED"))
                .andExpect(jsonPath("$.deliveries[0].outcome").value("ACCEPTED"));
        mvc.perform(get("/accounts/ACC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
        mvc.perform(get("/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsByStatus.PROCESSED").value(1))
                .andExpect(jsonPath("$.transactionsByStatus.REJECTED").value(1));
        mvc.perform(get("/transactions/NOPE")).andExpect(status().isNotFound());
    }
}
