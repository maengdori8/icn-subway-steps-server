package org.skilltransfer.icnsubway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.skilltransfer.icnsubway.Requests.LoginRequest;
import org.skilltransfer.icnsubway.Requests.SignupRequest;
import org.skilltransfer.icnsubway.Responses.LoginResponse;
import org.skilltransfer.icnsubway.Responses.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{4,24}$");
    private static final int PASSWORD_MIN_CHARS = 8;
    private static final int PASSWORD_MAX_BYTES = 72; // BCrypt가 실제로 쓰는 최대 길이

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** 없는 아이디로 로그인할 때도 BCrypt 비교를 한 번 해서 응답 시간으로 아이디 존재가 드러나지 않게 한다. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public UserResponse signup(SignupRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.username() == null || !USERNAME.matcher(req.username()).matches()) {
            errors.put("username", "소문자 영문·숫자·밑줄 4~24자로 입력하세요.");
        }
        String pw = req.password();
        if (pw == null || TextRules.length(pw) < PASSWORD_MIN_CHARS || utf8Bytes(pw) > PASSWORD_MAX_BYTES) {
            errors.put("password", "8자 이상, UTF-8 72바이트 이하로 입력하세요.");
        }
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        if (users.existsByUsername(req.username())) throw usernameTaken();
        try {
            AppUser saved = users.saveAndFlush(new AppUser(req.username(), encoder.encode(pw), Instant.now()));
            return new UserResponse(saved.getId(), saved.getUsername());
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken(); // 동시에 같은 이름으로 가입한 경우
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.username() == null || req.username().isBlank()) errors.put("username", "아이디를 입력하세요.");
        if (req.password() == null || req.password().isEmpty()) errors.put("password", "비밀번호를 입력하세요.");
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        AppUser user = users.findByUsername(req.username()).orElse(null);
        boolean tooLong = utf8Bytes(req.password()) > PASSWORD_MAX_BYTES;
        boolean ok = !tooLong && encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "아이디 또는 비밀번호가 맞지 않아요.", Map.of());
        }
        return new LoginResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds(), new UserResponse(user.getId(), user.getUsername()));
    }

    private static int utf8Bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "이미 사용 중인 아이디입니다.",
                Map.of("username", "이미 사용 중인 아이디입니다."));
    }
}
