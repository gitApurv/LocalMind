package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.llm.service.ModelProfileRegistry;
import com.localmind.support.OfflineModelCatalog;
import com.localmind.tools.config.SearxngProperties;
import com.localmind.tools.config.WebScraperProperties;
import com.localmind.tools.service.DateTimeTools;
import com.localmind.tools.service.DocumentSearchTools;
import com.localmind.tools.service.MathTools;
import com.localmind.tools.service.UserMemoryTools;
import com.localmind.tools.service.WebScraperTools;
import com.localmind.tools.service.WebSearchTools;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

class ToolSelectorTest {
    private final ModelProfileRegistry registry = new ModelProfileRegistry();
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);

    private static ToolCallback dummyCallback(String name) {
        return FunctionToolCallback.builder(name, (Map<String, Object> req) -> "ok").description(name)
                .inputType(Map.class).build();
    }

    private final ToolCallback dateCb = dummyCallback("dateTime");
    private final ToolCallback mathCb = dummyCallback("math");
    private final ToolCallback webSearchCb = dummyCallback("webSearch");
    private final ToolCallback webScraperCb = dummyCallback("webScraper");
    private final ToolCallback memoryCb = dummyCallback("memory");
    private final ToolCallback recallCb = dummyCallback("recall");
    private final ToolCallback docSearchCb = dummyCallback("docSearch");

    private final DateTimeTools dateTimeTools = new DateTimeTools() {
        @Override
        public ToolCallback toolCallback() {
            return dateCb;
        }
    };

    private final MathTools mathTools = new MathTools() {
        @Override
        public ToolCallback toolCallback() {
            return mathCb;
        }
    };

    private final WebSearchTools webSearchTools = new WebSearchTools(
            org.springframework.web.client.RestClient.create(), new SearxngProperties("http://localhost:8081")) {
        @Override
        public ToolCallback toolCallback() {
            return webSearchCb;
        }
    };

    private final WebScraperTools webScraperTools = new WebScraperTools(new WebScraperProperties(false)) {
        @Override
        public ToolCallback toolCallback() {
            return webScraperCb;
        }
    };

    private final UserMemoryTools userMemoryTools = new UserMemoryTools(null, null) {
        @Override
        public ToolCallback saveToolCallback() {
            return memoryCb;
        }

        @Override
        public ToolCallback recallToolCallback() {
            return recallCb;
        }
    };

    private final DocumentSearchTools documentSearchTools = new DocumentSearchTools(null) {
        @Override
        public ToolCallback toolCallback() {
            return docSearchCb;
        }
    };

    private final ToolSelector selector = new ToolSelector(new OfflineModelCatalog(registry), attachmentRepository,
            dateTimeTools, mathTools,
            webSearchTools, webScraperTools, userMemoryTools, documentSearchTools);

    @Test
    void returnsEmptyToolsForUnsupportedModel() {
        List<ToolCallback> tools = selector.selectTools("gemma3:1b", "calculate 2+2", "c1", true);
        assertTrue(tools.isEmpty());
    }

    @Test
    void returnsEmptyToolsWhenNotRequested() {
        List<ToolCallback> tools = selector.selectTools("qwen2.5:7b", "calculate 2+2", "c1", false);
        assertTrue(tools.isEmpty());
    }

    @Test
    void includesDocSearchOnlyWhenAttachmentsExist() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(false);

        List<ToolCallback> tools = selector.selectTools("qwen2.5:7b", "hello", "c1", true);
        assertFalse(tools.isEmpty());
        assertFalse(tools.contains(docSearchCb));
    }

    @Test
    void includesDocSearchWhenAttachmentsExist() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(true);

        List<ToolCallback> tools = selector.selectTools("qwen2.5:7b", "hello", "c1", true);
        assertTrue(tools.contains(docSearchCb));
    }

    @Test
    void includesWebScraperWhenUrlInMessage() {
        List<ToolCallback> tools = selector.selectTools("qwen2.5:7b", "check https://example.com for news", "c1", true);
        assertTrue(tools.contains(webScraperCb));
    }

    @Test
    void excludesWebScraperWhenNoUrlInMessage() {
        List<ToolCallback> tools = selector.selectTools("qwen2.5:7b", "hello world", "c1", true);
        assertFalse(tools.contains(webScraperCb));
    }
}
