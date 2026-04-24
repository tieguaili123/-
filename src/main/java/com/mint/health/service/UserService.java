package com.mint.health.service;

import com.mint.health.dto.RegisterRequest;
import com.mint.health.dto.UpdateUserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String REGISTER = "REGISTER";
    private static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";
    private static final String MAIL_FROM = "1474188825@qq.com";

    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;

    public UserService(JdbcTemplate jdbcTemplate, JavaMailSender mailSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
    }

    public Map<String, Object> register(RegisterRequest request) {
        validateRegisterRequest(request);
        String email = normalizeEmail(request.getEmail());
        log.info("register start username={}, email={}", request.getUsername(), email);
        verifyEmailCode(email, request.getVerificationCode(), REGISTER);

        Integer usernameCount = jdbcTemplate.queryForObject("select count(1) from user where username = ?", Integer.class, request.getUsername().trim());
        if (usernameCount != null && usernameCount > 0) throw new RuntimeException("用户名已存在");

        Integer emailCount = jdbcTemplate.queryForObject("select count(1) from user where email = ?", Integer.class, email);
        if (emailCount != null && emailCount > 0) throw new RuntimeException("QQ邮箱已被绑定");

        jdbcTemplate.update("insert into user(username, password, nickname, email, gender, age) values(?, ?, ?, ?, ?, ?)",
                request.getUsername().trim(), request.getPassword().trim(), request.getNickname().trim(), email, request.getGender(), request.getAge());
        log.info("register success username={}, email={}", request.getUsername(), email);
        return login(request.getUsername().trim(), request.getPassword().trim());
    }

    public Map<String, Object> login(String username, String password) {
        log.info("login start username={}", username);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select id, username, nickname, avatar, email, gender, age from user where username = ? and password = ? and status = 1",
                username, password);
        if (list.isEmpty()) throw new RuntimeException("用户名或密码错误");
        Map<String, Object> user = new LinkedHashMap<String, Object>(list.get(0));
        user.put("token", "token-" + user.get("id"));
        return user;
    }

    public Map<String, Object> getUserInfo(Long userId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select id, username, nickname, avatar, email, gender, age from user where id = ?", userId);
        if (list.isEmpty()) throw new RuntimeException("用户不存在");
        return list.get(0);
    }

    public Map<String, Object> updateUser(UpdateUserRequest request) {
        if (request.getUserId() == null) throw new RuntimeException("用户ID不能为空");
        Map<String, Object> current = getUserInfo(request.getUserId());
        String nickname = request.getNickname() == null || request.getNickname().trim().isEmpty() ? String.valueOf(current.get("nickname")) : request.getNickname().trim();
        String avatar = request.getAvatar() == null ? (String) current.get("avatar") : request.getAvatar();
        String email = request.getEmail() == null || request.getEmail().trim().isEmpty() ? stringValue(current.get("email")) : normalizeEmail(request.getEmail());

        if (!email.equals(stringValue(current.get("email")))) {
            Integer emailCount = jdbcTemplate.queryForObject("select count(1) from user where email = ? and id <> ?", Integer.class, email, request.getUserId());
            if (emailCount != null && emailCount > 0) throw new RuntimeException("该QQ邮箱已被其他账号使用");
        }

        if (request.getNewPassword() != null && !request.getNewPassword().trim().isEmpty()) {
            verifyPasswordChange(request, current, email);
            jdbcTemplate.update("update user set nickname = ?, avatar = ?, email = ?, password = ?, update_time = now() where id = ?",
                    nickname, avatar, email, request.getNewPassword().trim(), request.getUserId());
        } else {
            jdbcTemplate.update("update user set nickname = ?, avatar = ?, email = ?, update_time = now() where id = ?",
                    nickname, avatar, email, request.getUserId());
        }
        return getUserInfo(request.getUserId());
    }

    public void resetPassword(String username, String email, String verificationCode, String newPassword) {
        if (username == null || username.trim().isEmpty()) throw new RuntimeException("请输入用户名");
        String normalizedEmail = normalizeEmail(email);
        if (newPassword == null || newPassword.trim().isEmpty()) throw new RuntimeException("请输入新密码");

        Integer count = jdbcTemplate.queryForObject("select count(1) from user where username = ? and email = ?", Integer.class, username.trim(), normalizedEmail);
        if (count == null || count == 0) throw new RuntimeException("用户名与QQ邮箱不匹配");

        verifyEmailCode(normalizedEmail, verificationCode, RESET_PASSWORD);
        jdbcTemplate.update("update user set password = ?, update_time = now() where username = ?", newPassword.trim(), username.trim());
    }

    public Map<String, Object> sendEmailCode(String email, String scene) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(scene);
        String code = String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000));
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(3);
        log.info("send email code start email={}, scene={}, expireTime={}", normalizedEmail, normalizedScene, expireTime);

        jdbcTemplate.update("update email_verification_code set used = 1 where email = ? and scene = ? and used = 0", normalizedEmail, normalizedScene);
        jdbcTemplate.update("insert into email_verification_code(email, code, scene, expire_time, used) values(?, ?, ?, ?, 0)",
                normalizedEmail, code, normalizedScene, Timestamp.valueOf(expireTime));

        sendVerificationEmailAsync(normalizedEmail, code, normalizedScene);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("email", normalizedEmail);
        result.put("scene", normalizedScene);
        result.put("expireMinutes", 3);
        result.put("message", "验证码生成成功，邮件正在发送，请稍候查收");
        return result;
    }

    @Async
    public void sendVerificationEmailAsync(String email, String code, String scene) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(MAIL_FROM);
            message.setTo(email);
            message.setSubject(buildEmailSubject(scene));
            message.setText(buildEmailContent(code, scene));
            mailSender.send(message);
            log.info("send email code success email={}, scene={}", email, scene);
        } catch (Exception e) {
            log.error("send email code failed email={}, scene={}", email, scene, e);
        }
    }

    private String buildEmailSubject(String scene) {
        if (REGISTER.equals(scene)) return "薄荷轻食 - 注册验证码";
        if (CHANGE_PASSWORD.equals(scene)) return "薄荷轻食 - 修改密码验证码";
        return "薄荷轻食 - 找回密码验证码";
    }

    private String buildEmailContent(String code, String scene) {
        String action = "账号验证";
        if (REGISTER.equals(scene)) action = "注册账号";
        else if (CHANGE_PASSWORD.equals(scene)) action = "修改密码";
        else if (RESET_PASSWORD.equals(scene)) action = "找回密码";
        return "您好，\n\n您正在进行“" + action + "”操作。\n本次验证码为：" + code + "\n有效期：3分钟。\n\n如果这不是您的操作，请忽略此邮件。\n\n薄荷轻食";
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) throw new RuntimeException("请输入用户名");
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) throw new RuntimeException("请输入密码");
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) throw new RuntimeException("请输入昵称");
        if (request.getVerificationCode() == null || request.getVerificationCode().trim().isEmpty()) throw new RuntimeException("请输入邮箱验证码");
        normalizeEmail(request.getEmail());
    }

    private void verifyPasswordChange(UpdateUserRequest request, Map<String, Object> current, String email) {
        String verifyType = request.getVerifyType() == null ? "" : request.getVerifyType().trim().toUpperCase();
        if ("OLD_PASSWORD".equals(verifyType)) {
            if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) throw new RuntimeException("请输入旧密码");
            Integer count = jdbcTemplate.queryForObject("select count(1) from user where id = ? and password = ?", Integer.class, request.getUserId(), request.getOldPassword().trim());
            if (count == null || count == 0) throw new RuntimeException("旧密码不正确");
            return;
        }

        String currentEmail = stringValue(current.get("email"));
        if (currentEmail.isEmpty()) throw new RuntimeException("当前账号未绑定QQ邮箱，无法使用邮箱验证");
        if (!email.equals(currentEmail)) throw new RuntimeException("修改密码时请输入当前已绑定的QQ邮箱");
        verifyEmailCode(email, request.getVerificationCode(), CHANGE_PASSWORD);
    }

    private void verifyEmailCode(String email, String code, String scene) {
        if (code == null || code.trim().isEmpty()) throw new RuntimeException("请输入邮箱验证码");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select id, code, expire_time from email_verification_code where email = ? and scene = ? and used = 0 order by id desc limit 1",
                email, scene);
        if (list.isEmpty()) throw new RuntimeException("验证码不存在或已失效");

        Map<String, Object> row = list.get(0);
        String savedCode = String.valueOf(row.get("code"));
        LocalDateTime expireTime = toLocalDateTime(row.get("expire_time"));
        if (!savedCode.equals(code.trim())) throw new RuntimeException("邮箱验证码错误");
        if (expireTime == null || expireTime.isBefore(LocalDateTime.now())) {
            jdbcTemplate.update("update email_verification_code set used = 1 where id = ?", row.get("id"));
            throw new RuntimeException("邮箱验证码已过期，请重新获取");
        }
        jdbcTemplate.update("update email_verification_code set used = 1 where id = ?", row.get("id"));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        return null;
    }

    private String normalizeScene(String scene) {
        String value = scene == null ? "" : scene.trim().toUpperCase();
        if (REGISTER.equals(value) || CHANGE_PASSWORD.equals(value) || RESET_PASSWORD.equals(value)) return value;
        throw new RuntimeException("验证码场景不正确");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) throw new RuntimeException("请输入QQ邮箱");
        String value = email.trim().toLowerCase();
        if (!value.matches("^[a-zA-Z0-9._%+-]+@qq\\.com$")) throw new RuntimeException("请输入正确的QQ邮箱");
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
