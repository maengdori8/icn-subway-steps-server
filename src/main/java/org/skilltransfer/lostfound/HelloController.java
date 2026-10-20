package org.skilltransfer.lostfound;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class HelloController {
    @GetMapping("/") public String home() { return "7장 시작 프로젝트가 실행 중입니다."; }
    @GetMapping("/api/health") public Map<String,String> health() { return Map.of("status","UP","project","lost-found-api-starter"); }
}
