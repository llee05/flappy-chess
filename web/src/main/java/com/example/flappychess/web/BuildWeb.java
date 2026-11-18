package com.example.flappychess.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.builder.TeaBuilder;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;
import java.io.File;
import java.util.List;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Runs on the JDK during the build, producing files any static web host can serve. */
public final class BuildWeb {
    /**
     * Shared assets the browser game never reads. The backend preloads every copied asset before
     * the first frame, so these would only delay starting a game. The lobby serves its own
     * subset web fonts from {@code webapp/fonts}.
     */
    private static final List<String> EXCLUDED = List.of(
            "fonts/", "images/background.png", "images/bird.jpeg", "images/wings.png");

    public static void main(String[] args) {
        File output = new File(args[1]);
        WebBackend backend = new WebBackend().setGenerateIndexHtml(false);
        AssetFileHandle assets = new AssetFileHandle(args[0]);
        assets.filter = BuildWeb::preload;
        new TeaBuilder(backend)
                .addAssets(assets)
                .setMainClass(WebLauncher.class.getName())
                .setReleasePath(output)
                .setOutputName("game")
                .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
                .setObfuscated(true)
                .build(output);
    }

    static boolean preload(String file) {
        String path = file.replace('\\', '/');
        if (path.endsWith(".md") || path.endsWith(".svn") || path.endsWith(".jar")) return false;
        for (String excluded : EXCLUDED) {
            if (path.startsWith(excluded) || path.contains("/" + excluded)) return false;
        }
        return true;
    }
}
