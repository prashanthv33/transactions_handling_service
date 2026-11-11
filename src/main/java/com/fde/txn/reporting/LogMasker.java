package com.fde.txn.reporting;

/** Masks customer identifiers before they are written to logs. */
public final class LogMasker {

    private LogMasker() {
    }

    /** "ACC-201" becomes "ACC***1". Short or null values are fully masked. */
    public static String account(String accountId) {
        if (accountId == null || accountId.length() <= 4) {
            return "****";
        }
        return accountId.substring(0, 3) + "***" + accountId.charAt(accountId.length() - 1);
    }
}
