package com.localmind.tools.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class DateTimeTools {

    private static final String NAME = "getCurrentDateTime";
    private static final String DESCRIPTION = "Get the current date, time, day of the week, and local system timezone.";

    public record DateTimeRequest() {
    }

    public String getCurrentDateTime() {
        ZonedDateTime now = ZonedDateTime.now();
        return String.format("Current Date & Time: %s (%s, Timezone: %s)",
                now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), now.getDayOfWeek(), now.getZone().getId());
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder(NAME, (DateTimeRequest request) -> getCurrentDateTime())
                .description(DESCRIPTION).inputType(DateTimeRequest.class).build();
    }
}
