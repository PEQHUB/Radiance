package com.radiance.client.materiallab;

import com.radiance.client.autopbr.AutoPbrValue;
import java.util.List;

public final class MaterialBakePlan {
    public final boolean ok;
    public final int size;
    public final AutoPbrValue.Surface surface;
    public final List<String> diagnostics;
    public final MaterialPreviewData preview;

    public MaterialBakePlan(boolean ok, int size, AutoPbrValue.Surface surface, List<String> diagnostics) {
        this(ok, size, surface, diagnostics, null);
    }

    public MaterialBakePlan(boolean ok, int size, AutoPbrValue.Surface surface, List<String> diagnostics,
                            MaterialPreviewData preview) {
        this.ok = ok;
        this.size = size;
        this.surface = surface;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.preview = preview;
    }

    public static MaterialBakePlan failed(int size, String diagnostic) {
        return new MaterialBakePlan(false, size, null, List.of(diagnostic));
    }
}
