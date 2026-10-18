package com.example.flappychess.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.builder.TeaBuilder;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;
import java.io.File;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Runs on the JDK during the build, producing files any static web host can serve. */
public final class BuildWeb {
    public static void main(String[] args) {
        File output = new File(args[1]);
        WebBackend backend = new WebBackend().setGenerateIndexHtml(false);
        new TeaBuilder(backend)
                .addAssets(new AssetFileHandle(args[0]))
                .setMainClass(WebLauncher.class.getName())
                .setReleasePath(output)
                .setOutputName("game")
                .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
                .setObfuscated(true)
                .build(output);
    }
}
