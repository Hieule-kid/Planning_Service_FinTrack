package com.fintrack.planning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GeminiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not set. AI features will not work. " +
                     "Set the GEMINI_API_KEY environment variable to a valid Google AI Studio key (starts with 'AIza').");
        }
        return builder
                .defaultSystem("""
                        You are an expert personal finance advisor. Analyze the user's input and produce a budget breakdown.
                        If the user specifies explicit allocation percentages, follow them strictly.
                        If the user does not mention specific percentages, apply the standard 50/30/20 rule:
                          - 50% Necessities
                          - 30% Wants
                          - 20% Savings/Investments
                        Always set the `term` field to "MONTHLY" unless the user says otherwise.
                        Provide a short, practical note for each category.

                        IMPORTANT: Your response MUST be valid JSON only — no markdown, no code fences, no explanation outside the JSON object.
                        Return exactly this structure:
                        {
                          "totalBudget": <number>,
                          "currency": "<string>",
                          "term": "<string>",
                          "categories": [
                            { "name": "<string>", "percentage": <number>, "amount": <number>, "note": "<string>" }
                          ],
                          "aiAdvice": "<string>"
                        }
                        """)
                .build();
    }
}
