package org.ninestick.codekin.resume.render;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.resume.config.ResumePdfProperties;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/** 每次请求独立 Chromium 上下文；网络只允许内存提供的组件、字体及 data 图片。 @author ninestick */
@Slf4j @Component
public class ResumePdfRenderer {
    private static final String ASSET_ORIGIN = "https://resume-renderer.codekin.invalid";
    @Resource private ResumePdfProperties properties;
    private volatile Semaphore capacity;

    public byte[] render(ObjectNode snapshot) {
        if (capacity == null) synchronized (this) {
            if (capacity == null) capacity = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
        }
        if (!capacity.tryAcquire()) throw new BusinessException(ResumeCode.PDF_BUSY);
        String stage = "browser-start";
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            var options = new BrowserType.LaunchOptions().setHeadless(true).setChromiumSandbox(true)
                    .setTimeout(properties.getTimeoutMillis());
            if (StringUtils.isNotBlank(properties.getExecutablePath())) options.setExecutablePath(Path.of(properties.getExecutablePath()));
            try (Browser browser = playwright.chromium().launch(options);
                 BrowserContext context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(false)
                         .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setViewportSize(794, 1123))) {
                AtomicBoolean failedAsset = new AtomicBoolean();
                context.route("**/*", route -> {
                    try {
                        var uri = URI.create(route.request().url());
                        String path = uri.getPath();
                        if (!route.request().url().startsWith(ASSET_ORIGIN + "/")
                                || !path.matches("/(renderer\\.(js|css)|fonts/[A-Za-z0-9_-]+\\.(ttf|otf))")) {
                            failedAsset.set(true); route.abort(); return;
                        }
                        String type = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : "application/octet-stream";
                        route.fulfill(new Route.FulfillOptions().setContentType(type).setBodyBytes(asset(path.substring(1))));
                    } catch (Exception failure) { failedAsset.set(true); route.abort(); }
                });
                Page page = context.newPage();
                page.setDefaultTimeout(properties.getTimeoutMillis());
                stage = "component-load";
                page.onPageError(error -> log.warn("PDF component error type={}", error.split(":")[0]));
                page.setContent("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><base href=\"" + ASSET_ORIGIN
                        + "/\"><link rel=\"stylesheet\" href=\"renderer.css\"></head><body><div id=\"root\"></div><script src=\"renderer.js\"></script></body></html>");
                page.waitForFunction("typeof window.renderResume === 'function'");
                // Promise.race bounds font/image readiness as evaluate itself has no default timeout.
                stage = "snapshot-render";
                page.evaluate("json => Promise.race([window.renderResume(JSON.parse(json)), new Promise((_, reject) => setTimeout(() => reject(new Error('PDF render timeout')), "
                        + properties.getTimeoutMillis() + "))])", JsonUtils.toJson(snapshot));
                if (failedAsset.get()) throw new BusinessException(ResumeCode.PDF_RESOURCE_FAILED);
                stage = "pdf-print";
                return page.pdf(new Page.PdfOptions().setFormat("A4").setPreferCSSPageSize(true)
                        .setPrintBackground(true).setDisplayHeaderFooter(false).setTagged(true));
            }
        } catch (BusinessException failure) { throw failure; }
        catch (RuntimeException failure) {
            log.warn("PDF render failed stage={} type={}", stage, failure.getClass().getSimpleName());
            if (!"snapshot-render".equals(stage) && !"pdf-print".equals(stage)) log.warn("PDF bootstrap diagnostic: {}", failure.getMessage());
            throw new BusinessException(ResumeCode.PDF_FAILED);
        }
        finally { capacity.release(); }
    }

    private byte[] asset(String name) throws IOException {
        if (StringUtils.isNotBlank(properties.getAssetsDirectory())) {
            return Files.readAllBytes(Path.of(properties.getAssetsDirectory()).resolve(name));
        }
        try (var stream = new ClassPathResource("resume-pdf/" + name).getInputStream()) { return stream.readAllBytes(); }
    }
}
