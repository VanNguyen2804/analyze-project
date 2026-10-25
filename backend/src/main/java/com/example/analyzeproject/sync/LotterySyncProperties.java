package com.example.analyzeproject.sync;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "lottery.sync")
public class LotterySyncProperties {
    private boolean enabled = true;
    @Min(60) @Max(86400) private long intervalSeconds = 900;
    @Min(0) @Max(3600) private long startupDelaySeconds = 10;
    @Min(1) @Max(7300) private int lookbackDays = 90;
    @Min(1) @Max(120) private int timeoutSeconds = 20;
    private String megaUrl = "https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power645.jsonl";
    private String powerUrl = "https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power655.jsonl";

    public String sourceUrl(String category) {
        return "POWER".equals(category) ? powerUrl : megaUrl;
    }
}
