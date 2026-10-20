package org.skilltransfer.icnsubway;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class HelloController {
    @GetMapping("/") public String home() { return "ICN Subway Steps 서버가 실행 중입니다."; }
    @GetMapping("/api/health") public Map<String,String> health() { return Map.of("status","UP","project","icn-subway-server"); }
}
