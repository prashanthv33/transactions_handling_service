package com.fde.txn.domain;

/** A temporary failure (e.g. a downstream timeout) that is safe to retry. */
public class TransientProcessingException extends RuntimeException {

    public TransientProcessingException(String message) {
        super(message);
    }
}
