package com.radiance.client.gui.unified;

import com.radiance.client.gui.RadianceTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Scrollable right-side panel that contains SettingsSections.
 * Replaces WideOptionListWidget + OptionListWidget for the unified settings.
 *
 * Features:
 * - Smooth scrolling (lerp toward target)
 * - Edge fade (top/bottom entries fade near edges)
 * - Peek mode (Tab hides everything)
 * - Slider focus mode (inactive sections fade during drag)
 * - Subtle slide animation on content change
 */
public class ContentPanelWidget extends ClickableWidget {

    private final List<SettingsSection> sections = new ArrayList<>();

    // ── Scroll state ──
    private double scrollTarget = 0;
    private double scrollDisplay = 0;
    private boolean scrollInitialized = false;
    private static final float SCROLL_LERP_SPEED = 0.35f;
    private static final int SCROLL_STEP = 30;
    private int contentPadding = 4;

    // ── Content transition animation ──
    private long contentChangeMs = 0;
    private static final long CONTENT_ANIM_MS = 100;
    private boolean contentAnimating = false;

    /** The section that consumed the last mouseClicked — drags/releases route here. */
    private SettingsSection clickedSection;

    public ContentPanelWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
    }

    // ── Content management ──

    public void clearContent() {
        sections.clear();
        scrollTarget = 0;
        scrollDisplay = 0;
        scrollInitialized = false;
        contentChangeMs = System.currentTimeMillis();
        contentAnimating = true;
    }

    public SettingsSection addSection(Text title) {
        SettingsSection section = new SettingsSection(title);
        sections.add(section);
        return section;
    }

    public SettingsSection addSection(String translationKey) {
        return addSection(Text.translatable(translationKey));
    }

    public void addSection(SettingsSection section) {
        sections.add(section);
    }

    public List<SettingsSection> getSections() {
        return sections;
    }

    // ── Layout ──

    /** Total height of all sections content. */
    private int totalContentHeight() {
        int total = contentPadding;
        for (SettingsSection section : sections) {
            total += section.getHeight() + 4; // 4px gap between sections
        }
        return total + contentPadding;
    }

    /** Maximum scroll offset. */
    private double maxScroll() {
        return Math.max(0, totalContentHeight() - getHeight());
    }

    // ── Rendering ──

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;

        // Initialize scroll on first frame
        if (!scrollInitialized) {
            scrollDisplay = scrollTarget;
            scrollInitialized = true;
        }

        // Smooth scroll interpolation
        double diff = scrollTarget - scrollDisplay;
        if (Math.abs(diff) < 0.5) {
            scrollDisplay = scrollTarget;
        } else {
            scrollDisplay += diff * SCROLL_LERP_SPEED;
        }

        // Content transition alpha
        float contentAlpha = 1f;
        if (contentAnimating) {
            long elapsed = System.currentTimeMillis() - contentChangeMs;
            float t = Math.min(1f, elapsed / (float) CONTENT_ANIM_MS);
            contentAlpha = t;
            if (t >= 1f) contentAnimating = false;
        }

        // Global fade factor (slider focus)
        float globalFade = RadianceTheme.inactiveFadeFactor();

        // Enable scissor to clip content within panel bounds
        int panelLeft = getX();
        int panelTop = getY();
        int panelRight = panelLeft + getWidth();
        int panelBottom = panelTop + getHeight();

        // Full-panel dark background (hidden during slider drag)
        if (globalFade > 0.005f) {
            context.fill(panelLeft, panelTop, panelRight, panelBottom,
                RadianceTheme.scaleAlpha(RadianceTheme.unifiedContentBg, globalFade));
        }

        context.enableScissor(panelLeft, panelTop, panelRight, panelBottom);

        // Render sections
        int drawY = panelTop + contentPadding - (int) scrollDisplay;
        for (SettingsSection section : sections) {
            int sectionHeight = section.getHeight();

            // Skip if entirely off-screen
            if (drawY + sectionHeight < panelTop - 20) {
                drawY += sectionHeight + 4;
                continue;
            }
            if (drawY > panelBottom + 20) break;

            // Compute scroll-edge alpha for this section
            float scrollAlpha = RadianceTheme.scrollAlpha(drawY + sectionHeight / 2, panelTop, panelBottom);

            // All sections fade together during slider drag
            float sectionFade = globalFade;

            float combinedAlpha = scrollAlpha * sectionFade * contentAlpha;
            if (combinedAlpha > 0.005f) {
                // Subtle section backdrop (lighter than panel bg for depth)
                int backdropColor = RadianceTheme.withAlpha(0x141418, 0.4f * combinedAlpha);
                context.fill(panelLeft + 2, drawY - 1, panelRight - 2, drawY + sectionHeight + 1, backdropColor);

                section.render(context, panelLeft + contentPadding, drawY,
                    getWidth() - contentPadding * 2, mouseX, mouseY, delta, combinedAlpha);
            }

            drawY += sectionHeight + 4;
        }

        context.disableScissor();
    }

    // ── Input handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (RadianceTheme.peekActive) return false;

        clickedSection = null;
        int drawY = getY() + contentPadding - (int) scrollDisplay;
        for (SettingsSection section : sections) {
            int sectionHeight = section.getHeight();
            if (mouseY >= drawY && mouseY < drawY + sectionHeight) {
                if (section.mouseClicked(mouseX, mouseY, button,
                        getX() + contentPadding, drawY, getWidth() - contentPadding * 2)) {
                    clickedSection = section;
                    return true;
                }
                return false;
            }
            drawY += sectionHeight + 4;
        }
        return false;
    }

    /**
     * Route drag ONLY to the section that received the original click.
     * Minecraft's ClickableWidget.mouseDragged returns true unconditionally for
     * valid buttons, so iterating all sections would let the first widget steal drags.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (clickedSection != null) {
            return clickedSection.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clickedSection != null) {
            boolean result = clickedSection.mouseReleased(mouseX, mouseY, button);
            clickedSection = null;
            return result;
        }
        // Fallback
        for (SettingsSection section : sections) {
            if (section.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scrollTarget = MathHelper.clamp(scrollTarget - verticalAmount * SCROLL_STEP, 0, maxScroll());
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    // ── Scroll position API (for menu position persistence) ──

    public double getScrollTarget() { return scrollTarget; }

    public void setScrollTarget(double target) {
        this.scrollTarget = target;
        this.scrollDisplay = target;
        this.scrollInitialized = true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Minimal narration for now
    }
}
