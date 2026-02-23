package org.doc.util.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversionResult {

    private String collectionName;
    private String postmanJson;
    private String swaggerYaml;
    private int postmanTokens;
    private int swaggerTokens;

    /** Positive = Postman is larger (swagger saves tokens), negative = swagger is larger */
    private int tokenDifference;

    // ── Pre-computed for Thymeleaf (avoids OGNL arithmetic/comparison issues) ──

    /** Absolute reduction as a percentage string, e.g. "8.9" */
    private String reductionPct;

    /** "saves" or "costs" */
    private String savingsVerb;

    /** Bar fill width 0–100 (capped) */
    private int barWidth;

    /** true when swagger is the smaller format */
    private boolean swaggerSmaller;

    /** Postman JSON char count as formatted string */
    private String postmanChars;

    /** Swagger YAML char count as formatted string */
    private String swaggerChars;

    public static ConversionResult of(String collectionName,
                                      String postmanJson,
                                      String swaggerYaml,
                                      int postmanTokens,
                                      int swaggerTokens) {
        int diff = postmanTokens - swaggerTokens;
        boolean smaller = diff > 0;
        double pct = postmanTokens == 0 ? 0.0 : Math.abs((double) diff / postmanTokens * 100.0);
        int bar = (int) Math.min(100, pct);

        return ConversionResult.builder()
                .collectionName(collectionName)
                .postmanJson(postmanJson)
                .swaggerYaml(swaggerYaml)
                .postmanTokens(postmanTokens)
                .swaggerTokens(swaggerTokens)
                .tokenDifference(diff)
                .reductionPct(String.format("%.1f", pct))
                .savingsVerb(smaller ? "saves" : "costs")
                .barWidth(bar)
                .swaggerSmaller(smaller)
                .postmanChars(String.format("%,d", postmanJson.length()))
                .swaggerChars(String.format("%,d", swaggerYaml.length()))
                .build();
    }
}
