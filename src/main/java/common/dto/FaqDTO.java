package common.dto;

import java.io.Serializable;

/**
 * DTO for FAQ search results.
 */
public class FaqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String question;
    private String answer;
    private double relevanceScore;

    public FaqDTO(String question, String answer, double relevanceScore) {
        this.question = question;
        this.answer = answer;
        this.relevanceScore = relevanceScore;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }
}
