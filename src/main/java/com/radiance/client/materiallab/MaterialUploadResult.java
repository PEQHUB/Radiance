package com.radiance.client.materiallab;

import java.util.List;

public final class MaterialUploadResult {
    public final MaterialBakePlan plan;
    public final boolean specularOk;
    public final boolean normalOk;
    public final boolean heightMetadataOk;
    public final boolean textureRulesOk;
    public final boolean uploadOk;
    public final String statusText;
    public final List<String> diagnostics;

    public MaterialUploadResult(MaterialBakePlan plan,
                                boolean specularOk,
                                boolean normalOk,
                                boolean heightMetadataOk,
                                boolean textureRulesOk,
                                List<String> diagnostics) {
        this.plan = plan;
        this.specularOk = specularOk;
        this.normalOk = normalOk;
        this.heightMetadataOk = heightMetadataOk;
        this.textureRulesOk = textureRulesOk;
        this.uploadOk = plan != null && plan.ok && specularOk && normalOk && heightMetadataOk && textureRulesOk;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.statusText = statusText();
    }

    public static MaterialUploadResult bakeFailed(MaterialBakePlan plan) {
        return new MaterialUploadResult(plan, false, false, false, false,
            plan == null ? List.of("unknown bake failure") : plan.diagnostics);
    }

    private String statusText() {
        if (plan == null || !plan.ok) return "Bake Error";
        if (uploadOk) return "Applied";
        if (diagnostics.stream().anyMatch(d -> d.contains("native link unavailable"))) return "Native Skipped";
        if (!textureRulesOk) return "Rules Pending/Failed";
        return "Upload Failed";
    }
}
