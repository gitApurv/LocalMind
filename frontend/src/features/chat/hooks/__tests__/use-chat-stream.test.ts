import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useChatStream } from "../use-chat-stream";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { useModelStore } from "@/features/models/store/model-store";
import { aConversation, aMessage, resetWorkspaceStores } from "@/test/factories";
import { useChatStore } from "../../store/chat-store";
import { apiClient } from "@/lib/api-client";

describe("useChatStream", () => {
  beforeEach(() => {
    resetWorkspaceStores();
    useConversationStore.setState({
      conversations: [aConversation({ id: "c-123", title: "Test Thread" })],
      activeConversationId: "c-123",
    });
    useModelStore.setState({ selectedModel: "qwen2.5:7b", toolsEnabled: true });
    vi.restoreAllMocks();
    apiClient.setAccessToken("test-jwt-token");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function createSseStream(chunks: string[]) {
    const encoder = new TextEncoder();
    return new ReadableStream({
      start(controller) {
        for (const chunk of chunks) {
          controller.enqueue(encoder.encode(chunk));
        }
        controller.close();
      },
    });
  }

  it("should stream reasoning, tool execution, and content events into message state", async () => {
    const sseChunks = [
      'data: {"type":"TOOL_START","tool":"searchWeb"}\n\n',
      'data: {"type":"TOOL_END","tool":"searchWeb"}\n\n',
      'data: {"type":"REASONING","content":"Let me "}\n\n',
      'data: {"type":"REASONING","content":"think about this."}\n\n',
      'data: {"type":"CONTENT","content":"Here is "}\n\n',
      'data: {"type":"CONTENT","content":"the answer."}\n\n',
      'data: {"type":"DONE","messageId":"msg-real-456"}\n\n',
    ];

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createSseStream(sseChunks),
    } as unknown as Response);

    const { result } = renderHook(() => useChatStream());

    await act(async () => {
      await result.current.sendMessage("What is the capital of France?");
    });

    const state = useChatStore.getState();
    expect(state.isStreaming).toBe(false);
    expect(state.messages).toHaveLength(2);

    const userMsg = state.messages[0];
    expect(userMsg.role).toBe("USER");
    expect(userMsg.content).toBe("What is the capital of France?");

    const assistantMsg = state.messages[1];
    expect(assistantMsg.role).toBe("ASSISTANT");
    expect(assistantMsg.id).toBe("msg-real-456");
    expect(assistantMsg.reasoningContent).toBe("Let me think about this.");
    expect(assistantMsg.content).toBe("Here is the answer.");
    expect(assistantMsg.toolsUsed).toEqual(["searchWeb"]);
  });

  it("sends the selected model, tools flag and bearer token to /api/chat/stream", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      body: createSseStream(['data: {"type":"DONE","messageId":"msg-1"}\n\n']),
    } as unknown as Response);
    global.fetch = fetchMock;

    const { result } = renderHook(() => useChatStream());
    await act(async () => {
      await result.current.sendMessage("  Hello  ");
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/chat/stream");
    expect(JSON.parse(init.body)).toEqual({
      conversationId: "c-123",
      message: "Hello",
      model: "qwen2.5:7b",
      toolsEnabled: true,
    });
    expect(init.headers.get("Authorization")).toBe("Bearer test-jwt-token");
    expect(init.headers.get("Accept")).toBe("text/event-stream");
  });

  it("shows the backend ProblemDetail message when the stream request is rejected", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: "Bad Request",
      headers: new Headers({ "content-type": "application/problem+json" }),
      json: async () => ({ status: 400, message: "Model not available" }),
    } as unknown as Response);

    const { result } = renderHook(() => useChatStream());
    await act(async () => {
      await result.current.sendMessage("Hi");
    });

    expect(result.current.error).toBe("Model not available");
    const lastMessage = useChatStore.getState().messages[1];
    expect(lastMessage.error).toBe(true);
    expect(lastMessage.content).toBe("⚠️ Model not available");
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it("should handle CRLF line endings (\\r\\n\\r\\n) from proxies", async () => {
    const sseChunks = [
      'data: {"type":"CONTENT","content":"CRLF content"}\r\n\r\n',
      'data: {"type":"DONE","messageId":"msg-crlf"}\r\n\r\n',
    ];

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createSseStream(sseChunks),
    } as unknown as Response);

    const { result } = renderHook(() => useChatStream());

    await act(async () => {
      await result.current.sendMessage("Test CRLF");
    });

    const state = useChatStore.getState();
    expect(state.messages[1].content).toBe("CRLF content");
    expect(state.messages[1].id).toBe("msg-crlf");
  });

  it("should support user stopStreaming() cancellation via AbortController", async () => {
    let cancelCalled = false;
    const encoder = new TextEncoder();

    global.fetch = vi.fn().mockImplementation((_url, init) => {
      const signal: AbortSignal | undefined = init?.signal;
      const stream = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(
            encoder.encode('data: {"type":"CONTENT","content":"Starting..."}\n\n')
          );
          if (signal) {
            signal.addEventListener("abort", () => {
              cancelCalled = true;
              try {
                controller.error(new DOMException("The user aborted a request.", "AbortError"));
              } catch {}
            });
          }
        },
      });

      return Promise.resolve({
        ok: true,
        body: stream,
      });
    });

    const { result } = renderHook(() => useChatStream());

    // Start sending
    let sendPromise: Promise<void>;
    act(() => {
      sendPromise = result.current.sendMessage("Hello!");
    });

    expect(useChatStore.getState().isStreaming).toBe(true);

    // Stop streaming
    act(() => {
      result.current.stopStreaming();
    });

    await act(async () => {
      await sendPromise;
    });

    expect(cancelCalled).toBe(true);
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it("stops the stream when another thread is opened, leaving that thread untouched", async () => {
    const encoder = new TextEncoder();
    let pushChunk: (text: string) => void = () => undefined;
    let requestSignal: AbortSignal | undefined;
    global.fetch = vi.fn().mockImplementation((_url, init) => {
      requestSignal = init?.signal;
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          pushChunk = (text) => controller.enqueue(encoder.encode(text));
        },
      });
      return Promise.resolve({ ok: true, body });
    });
    useConversationStore.setState({
      conversations: [aConversation({ id: "c-123" }), aConversation({ id: "c-2" })],
    });
    const otherThread = [aMessage({ id: "m-2", conversationId: "c-2", content: "Other thread" })];
    vi.spyOn(apiClient, "get").mockResolvedValue(otherThread);

    const { result } = renderHook(() => useChatStream());
    let sendPromise: Promise<void> = Promise.resolve();
    act(() => {
      sendPromise = result.current.sendMessage("Long question");
    });
    await act(async () => {
      pushChunk('data: {"type":"CONTENT","content":"First"}\n\n');
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(useChatStore.getState().messages[1].content).toBe("First");

    await act(async () => {
      await useConversationStore.getState().selectConversation("c-2");
    });
    try {
      pushChunk('data: {"type":"CONTENT","content":" leaked"}\n\ndata: {"type":"DONE"}\n\n');
    } catch {
      // The reader may already be cancelled, which is the point.
    }
    await act(async () => {
      await sendPromise;
    });

    expect(requestSignal?.aborted).toBe(true);
    expect(useChatStore.getState().messages).toEqual(otherThread);
    expect(useChatStore.getState().isStreaming).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("should handle error event from stream", async () => {
    const sseChunks = [
      'data: {"type":"CONTENT","content":"Partial content"}\n\n',
      'data: {"type":"ERROR","content":"Ollama context overflow"}\n\n',
    ];

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createSseStream(sseChunks),
    } as unknown as Response);

    const { result } = renderHook(() => useChatStream());

    await act(async () => {
      await result.current.sendMessage("Trigger error");
    });

    const state = useChatStore.getState();
    expect(state.isStreaming).toBe(false);
    expect(result.current.error).toBe("Ollama context overflow");
    const lastMsg = state.messages[state.messages.length - 1];
    expect(lastMsg.error).toBe(true);
  });

  it("should automatically create conversation when activeConversationId is null", async () => {
    useConversationStore.setState({ activeConversationId: null, conversations: [] });

    const newConv = aConversation({ id: "c-auto-created", title: "New Chat" });

    vi.spyOn(apiClient, "post").mockResolvedValueOnce(newConv);

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createSseStream(['data: {"type":"DONE","messageId":"msg-1"}\n\n']),
    } as unknown as Response);

    const { result } = renderHook(() => useChatStream());

    await act(async () => {
      await result.current.sendMessage("First question");
    });

    expect(useConversationStore.getState().activeConversationId).toBe("c-auto-created");
  });
});
