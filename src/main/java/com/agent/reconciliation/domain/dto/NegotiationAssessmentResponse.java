package com.agent.reconciliation.domain.dto;

/**
 * Result of Gemini AI negotiation evaluation of a supplier counter-explanation.
 */
public record NegotiationAssessmentResponse(
        boolean validArgument,
        String aiRecommendation,
        String auditorAdvice
) {}
