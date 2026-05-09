package com.nook.framework.web;

import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.util.function.Consumer;

/** 流式 chunked transfer-encoding 接口的胶水: 异步执行 + 行级 send + 异常 → completeWithError. */
@Slf4j
@Component
public class StreamingEndpointSupport {

    @Resource
    private AsyncTaskExecutor asyncExecutor;

    /**
     * 把 action 包成一个 ResponseBodyEmitter; action 接收 lineSink, 通过它逐行回写客户端.
     * action 内业务异常 → completeWithError, 已经在 emitter 上发送 [error] 行做最后兜底.
     */
    public ResponseBodyEmitter stream(String tag, Duration timeout, Consumer<Consumer<String>> action) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(timeout.toMillis());
        long start = System.currentTimeMillis();
        log.info("[stream:{}] start", tag);

        // emitter.send 必须在另一个线程, 否则会阻塞 Spring MVC 主线程发请求 (没法及时 flush)
        asyncExecutor.execute(() -> {
            try {
                action.accept(line -> trySend(emitter, line + "\n", tag));
                emitter.complete();
                log.info("[stream:{}] OK elapsed={}ms", tag, System.currentTimeMillis() - start);
            } catch (BusinessException be) {
                log.warn("[stream:{}] FAIL code={} msg={} elapsed={}ms",
                        tag, be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
                trySend(emitter, "[error] " + be.getMessage() + "\n", tag);
                emitter.completeWithError(be);
            } catch (Exception e) {
                log.error("[stream:{}] UNEXPECTED elapsed={}ms",
                        tag, System.currentTimeMillis() - start, e);
                trySend(emitter, "[error] " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n", tag);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** emitter 已完成 / 客户端已断开时 send 会抛, 静默吞掉避免覆盖原始失败原因. */
    private static void trySend(ResponseBodyEmitter emitter, String text, String tag) {
        try {
            emitter.send(text, MediaType.TEXT_PLAIN);
        } catch (Exception ignored) {
            // 多半是 emitter 已完成或客户端断开; 这里不应再触发 emitter.completeWithError 形成双重失败
            log.debug("[stream:{}] send 失败 (客户端断开?) text={}", tag, text.trim());
        }
    }
}
