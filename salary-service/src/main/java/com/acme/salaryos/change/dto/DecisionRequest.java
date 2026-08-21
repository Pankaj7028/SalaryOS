package com.acme.salaryos.change.dto;

/** Body for approve/reject — a decision note is encouraged, not required (the DB column is nullable). */
public record DecisionRequest(String decisionNote) {
}
