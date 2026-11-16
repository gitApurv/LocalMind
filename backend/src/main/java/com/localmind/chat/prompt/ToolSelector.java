package com.localmind.chat.prompt;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.llm.service.ModelCatalogService;
import com.localmind.tools.service.DateTimeTools;
import com.localmind.tools.service.DocumentSearchTools;
import com.localmind.tools.service.MathTools;
import com.localmind.tools.service.UserMemoryTools;
import com.localmind.tools.service.WebScraperTools;
import com.localmind.tools.service.WebSearchTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Chooses the tools offered to the model for one chat turn. Context-specific tools (web scraping, document search) are
 * only offered when they can be useful.
 */
@Component
@RequiredArgsConstructor
public class ToolSelector {

    private static final List<String> URL_MARKERS = List.of("http://", "https://", "www.");

    private final ModelCatalogService modelCatalogService;
    private final AttachmentRepository attachmentRepository;
    private final DateTimeTools dateTimeTools;
    private final MathTools mathTools;
    private final WebSearchTools webSearchTools;
    private final WebScraperTools webScraperTools;
    private final UserMemoryTools userMemoryTools;
    private final DocumentSearchTools documentSearchTools;

    public List<ToolCallback> selectTools(String modelName, String message, String conversationId,
            boolean toolsRequested) {
        if (!toolsRequested || !modelCatalogService.supportsTools(modelName)) {
            return List.of();
        }

        List<ToolCallback> selected = new ArrayList<>();
        ToolCallback dateTime = dateTimeTools.toolCallback();
        addIfPresent(selected, dateTime);
        ToolCallback math = mathTools.toolCallback();
        addIfPresent(selected, math);
        ToolCallback saveMemory = userMemoryTools.saveToolCallback();
        addIfPresent(selected, saveMemory);
        ToolCallback recallMemory = userMemoryTools.recallToolCallback();
        addIfPresent(selected, recallMemory);
        ToolCallback webSearch = webSearchTools.toolCallback();
        addIfPresent(selected, webSearch);
        if (containsUrl(message)) {
            ToolCallback webScraper = webScraperTools.toolCallback();
            addIfPresent(selected, webScraper);
        }
        if (hasReadyAttachments(conversationId)) {
            ToolCallback documentSearch = documentSearchTools.toolCallback();
            addIfPresent(selected, documentSearch);
        }
        return selected;
    }

    private boolean hasReadyAttachments(String conversationId) {
        return conversationId != null
                && attachmentRepository.existsByConversationIdAndStatus(conversationId, Attachment.Status.READY);
    }

    private static boolean containsUrl(String message) {
        String text = message != null ? message.toLowerCase(Locale.ROOT) : "";
        return URL_MARKERS.stream().anyMatch(text::contains);
    }

    private static void addIfPresent(List<ToolCallback> tools, ToolCallback tool) {
        if (tool != null) {
            tools.add(tool);
        }
    }
}
