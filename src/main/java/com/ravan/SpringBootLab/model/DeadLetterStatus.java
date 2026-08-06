package com.ravan.SpringBootLab.model;

public enum DeadLetterStatus {
    RECEIVED,
    QUARANTINED,
    REPLAYING,
    REPLAYED
}
