package com.mint.health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.provider:tongyi-qianwen}")
    private String provider;
    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:qwen-turbo}")
    private String model;
    @Value("${app.ai.vision-model:qwen-vl-max-latest}")
    private String visionModel;
    @Value("${app.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String baseUrl;

    public AiService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> recognizeFood(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("请上传图片");
        ensureApiKey();
        try {
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.trim().isEmpty()) mimeType = "image/jpeg";
            String image = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
            String raw = callVisionModel(image);
            List<Map<String, Object>> items = parseRecognizedItems(raw);
            if (items.isEmpty()) throw new RuntimeException("未识别到食物，请更换清晰图片再试");
            String imageName = file.getOriginalFilename() == null ? "uploaded-image" : file.getOriginalFilename();
            String parsedJson = objectMapper.writeValueAsString(items);
            jdbcTemplate.update("insert into ai_recognition_record(user_id, image_url, recognition_result, final_result, status, update_time) values(?, ?, ?, ?, ?, now())", userId, imageName, raw, parsedJson, "CONFIRMED");
            Long recordId = jdbcTemplate.queryForObject("select max(id) from ai_recognition_record where user_id = ?", Long.class, userId);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("recognitionId", recordId);
            result.put("imageUrl", imageName);
            result.put("items", items);
            result.put("message", "已通过千问视觉模型完成识别");
            result.put("provider", provider);
            result.put("model", visionModel);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("图片识别异常：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> chat(Long userId, String question) {
        if (question == null || question.trim().isEmpty()) throw new RuntimeException("问题不能为空");
        ensureApiKey();
        Map<String, Object> profile = null;
        List<Map<String, Object>> profileList = jdbcTemplate.queryForList("select * from user_body_profile where user_id = ?", userId);
        if (!profileList.isEmpty()) profile = profileList.get(0);
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<Map<String, Object>> records = jdbcTemplate.queryForList("select meal_type, total_calories from diet_record where user_id = ? and record_date = ?", userId, today);
        double total = 0;
        for (Map<String, Object> record : records) total += Double.parseDouble(String.valueOf(record.get("total_calories")));
        String answer = sanitizeAnswer(callChatModel(question.trim(), buildContext(profile, records, total, today)));
        jdbcTemplate.update("insert into ai_chat_record(user_id, question, answer) values(?, ?, ?)", userId, question.trim(), answer);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("answer", answer);
        result.put("provider", provider);
        result.put("model", model);
        return result;
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new RuntimeException("未配置 AI API Key");
    }

    private List<Map<String, Object>> parseRecognizedItems(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        JsonNode itemsNode = root.isArray() ? root : root.path("items");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!itemsNode.isArray()) return result;
        for (JsonNode node : itemsNode) {
            String foodName = node.path("foodName").asText("").trim();
            if (foodName.isEmpty()) foodName = node.path("name").asText("").trim();
            if (foodName.isEmpty()) continue;
            double weight = node.path("weight").asDouble(100D);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("foodName", foodName);
            item.put("suggestWeight", weight <= 0 ? 100D : weight);
            attachMatchedFood(item, foodName);
            result.add(item);
        }
        return result;
    }

    private void attachMatchedFood(Map<String, Object> item, String foodName) {
        List<Map<String, Object>> matched = jdbcTemplate.queryForList("select id, food_name from food where food_name = ? or food_name like ? or ? like concat('%', food_name, '%') order by case when food_name = ? then 0 else 1 end, length(food_name) asc limit 1", foodName, "%" + foodName + "%", foodName, foodName);
        if (!matched.isEmpty()) {
            item.put("foodId", matched.get(0).get("id"));
            item.put("foodName", String.valueOf(matched.get(0).get("food_name")));
        }
    }

    private String extractJson(String text) {
        String content = String.valueOf(text == null ? "" : text).trim();
        int a = content.indexOf('{'), b = content.lastIndexOf('}');
        if (a >= 0 && b > a) return content.substring(a, b + 1);
        int c = content.indexOf('['), d = content.lastIndexOf(']');
        if (c >= 0 && d > c) return content.substring(c, d + 1);
        throw new RuntimeException("识别结果不是有效 JSON：" + content);
    }

    private String buildContext(Map<String, Object> profile, List<Map<String, Object>> records, double total, String today) {
        StringBuilder builder = new StringBuilder();
        builder.append("今天日期：").append(today).append("。\n");
        builder.append("用户今日累计摄入热量：").append(Math.round(total * 100.0) / 100.0).append(" kcal。\n");
        if (profile != null) {
            builder.append("用户身体档案：");
            appendIfPresent(builder, "身高", profile.get("height"));
            appendIfPresent(builder, "体重", profile.get("weight"));
            appendIfPresent(builder, "目标", profile.get("goal"));
            appendIfPresent(builder, "BMI", profile.get("bmi"));
            appendIfPresent(builder, "建议热量", profile.get("recommended_calories"));
            builder.append("\n");
        }
        if (!records.isEmpty()) {
            builder.append("今日各餐记录：");
            for (Map<String, Object> record : records) builder.append(record.get("meal_type")).append("=").append(record.get("total_calories")).append("kcal；");
            builder.append("\n");
        }
        builder.append("请基于以上信息，用简洁、可执行、自然的中文回答。不要使用 markdown，不要使用 **、#、```、表格。可以分点，但请直接输出普通文本。");
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String label, Object value) {
        if (value != null && String.valueOf(value).trim().length() > 0) builder.append(label).append(":").append(String.valueOf(value)).append("；");
    }

    private String callChatModel(String question, String context) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", model);
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> system = new LinkedHashMap<String, String>();
        system.put("role", "system");
        system.put("content", "你是一名薄荷健康饮食助手，请根据用户身体档案和当日饮食记录，给出专业、简洁、可执行的中文建议。请只输出普通文本，不要输出 markdown 标记。");
        messages.add(system);
        Map<String, String> user = new LinkedHashMap<String, String>();
        user.put("role", "user");
        user.put("content", context + "\n用户问题：" + question);
        messages.add(user);
        payload.put("messages", messages);
        payload.put("temperature", 0.7);
        return callModel(payload);
    }

    private String callVisionModel(String imageDataUrl) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", visionModel);
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        Map<String, Object> system = new LinkedHashMap<String, Object>();
        system.put("role", "system");
        system.put("content", "你是一个食物识别助手。请识别图片中的食物，并只返回 JSON，不要解释，不要 markdown。格式必须是 {\"items\":[{\"foodName\":\"米饭\",\"weight\":150}]}。");
        messages.add(system);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("role", "user");
        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        Map<String, Object> imagePart = new LinkedHashMap<String, Object>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new LinkedHashMap<String, String>();
        imageUrl.put("url", imageDataUrl);
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);
        Map<String, Object> textPart = new LinkedHashMap<String, Object>();
        textPart.put("type", "text");
        textPart.put("text", "请识别这张图中的食物并估算克数，只返回 JSON。");
        content.add(textPart);
        user.put("content", content);
        messages.add(user);
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        return callModel(payload);
    }

    private String callModel(Map<String, Object> payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            String body = objectMapper.writeValueAsString(payload);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readStream(stream);
            if (status < 200 || status >= 300) throw new RuntimeException("大模型调用失败：" + responseText);
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isArray() && contentNode.size() > 0 && contentNode.get(0).has("text")) return contentNode.get(0).get("text").asText("").trim();
            String answer = contentNode.isMissingNode() ? "" : contentNode.asText();
            if (answer == null || answer.trim().isEmpty()) throw new RuntimeException("大模型返回内容为空：" + responseText);
            return answer.trim();
        } catch (Exception e) {
            throw new RuntimeException("大模型调用异常：" + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String sanitizeAnswer(String answer) {
        return String.valueOf(answer).replace("**", "").replace("```", "").replace("`", "").replaceAll("(?m)^#{1,6}\\s*", "").replace("•", "·").replace("●", "·").replaceAll("\\r", "").replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }
}
