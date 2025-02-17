package com.example.gemini_server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private static final Dotenv dotenv = Dotenv.load();
    private final String geminiApiUrl = dotenv.get("GEMINI_API_URL");
    private final String apiKey = dotenv.get("GEMINI_API_KEY");

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 문제를 생성하고, 즉시 정답을 예측하는 API
     */
    @PostMapping("/generate-and-predict")
    public ResponseEntity<Map<String, String>> generateAndPredict(@RequestBody Map<String, String> requestPayload) {
        String level = requestPayload.get("level");
        String language = requestPayload.get("language");

        // 1️⃣ 문제 생성 요청
        String question = generateQuestion(level, language);
        if (question.equals("문제를 찾을 수 없습니다.")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "문제 생성 실패"));
        }

        // 2️⃣ 생성된 문제로 정답 예측 요청 (빈칸 답만 요구)
        String answer = predictAnswer(question);

        // 4️⃣ 결과 반환
        Map<String, String> result = new HashMap<>();
        result.put("문제", question);
        result.put("답", answer);

        return ResponseEntity.ok(result);
    }

    /**
     * Gemini에게 문제 생성 요청
     */
    private String generateQuestion(String level, String language) {
        String prompt = level + " " + language + " 빈칸 문제를 만들어줘. 정답은 알려주지마.";

        String requestBody = createGeminiRequest(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                geminiApiUrl + "?key=" + apiKey, HttpMethod.POST, entity, String.class);

        return extractQuestion(response.getBody());
    }

    /**
     * Gemini에게 빈칸 정답 요청 (빈칸에 들어갈 정확한 값만 반환)
     */
    private String predictAnswer(String question) {
        // ✅ 더 강한 요청: 정답만 출력하도록 프롬프트 수정
        String prompt = "다음 문제의 빈칸(____)에 들어갈 정답 **하나만 출력해**.\n"
                      + "다른 문장은 절대 포함하지 마.\n"
                      + "빈칸에 들어갈 **정확한 단어 또는 코드**만 말해.\n"
                      + "예제: `+=`, `15`, `print` 같은 단어만 출력해.\n\n" + question;
    
        String requestBody = createGeminiRequest(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                geminiApiUrl + "?key=" + apiKey, HttpMethod.POST, entity, String.class);
    
        return cleanAnswer(extractAnswer(response.getBody()));
    }
    
    /**
     * ✅ 정답에서 불필요한 텍스트를 제거하는 함수
     */
    private String cleanAnswer(String answer) {
        // ✅ "왕초급" 같은 불필요한 문장 제거
        answer = answer.replaceAll("왕초급", "").trim();
    
        // ✅ "____의 값은?" 같은 문장 제거
        if (answer.contains("의 값은?")) {
            answer = answer.split("의 값은?")[0].trim();
        }
    
        // ✅ 첫 줄만 반환하여 이상한 문구 제거
        if (answer.contains("\n")) {
            return answer.split("\n")[0].trim();
        }
        return answer.trim();
    }
    /**
     * Gemini API 요청 JSON 생성
     */
    private String createGeminiRequest(String prompt) {
        return String.format(
                "{"
                        + "\"contents\": [{"
                        + "   \"role\": \"user\","
                        + "   \"parts\": [{"
                        + "       \"text\": \"%s\""
                        + "   }]"
                        + "}],"
                        + "\"systemInstruction\": {"
                        + "   \"role\": \"user\","
                        + "   \"parts\": [{"
                        + "       \"text\": \"문제 내용은 문장으로\\n"
                        + "빈칸 문제\\n"
                        + "만 적어줘\\n"
                        + "코딩 언어는 내가 지정해줄거야.\\n"
                        + "단계는 왕초급, 초급, 중급, 고급이 있어.\\n"
                        + "난이도는 너가 적절히 일관성있게 적용해야해.\\n"
                        + "네 같은 대답할 필요는 없어.\\n"
                        + "문제는 1개야, 정답도 빈칸은 1개로하여 정답도 하나가 나오게 할거야\\n"
                        + "답은 알려주지말아봐\\n"
                        + "문제만 출제해\""
                        + "   }]"
                        + "},"
                        + "\"generationConfig\": {"
                        + "   \"temperature\": 1,"
                        + "   \"topK\": 40,"
                        + "   \"topP\": 0.95,"
                        + "   \"maxOutputTokens\": 8192,"
                        + "   \"responseMimeType\": \"text/plain\""
                        + "}"
                        + "}", prompt.replace("\"", "\\\""));
    }

    /**
     * Gemini 응답에서 문제 추출
     */
    private String extractQuestion(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText().trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "문제를 찾을 수 없습니다.";
    }

    /**
     * Gemini 응답에서 정답 추출 (빈칸 정답만 가져오기)
     */
    private String extractAnswer(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText().trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "정답을 찾을 수 없습니다.";
    }
}