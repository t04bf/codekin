import type { CSSProperties } from "react";
import { useMemo } from "react";
import { GlobalSettings } from "@/types/resume";
import { useTemplateContext } from "../../TemplateContext";

interface SectionTitleProps {
    globalSettings?: GlobalSettings;
    type: string;
    title?: string;
    showTitle?: boolean;
}

const SectionTitle = ({ type, title, globalSettings, showTitle = true }: SectionTitleProps) => {
    const templateContext = useTemplateContext();
    const menuSections = templateContext?.menuSections ?? [];

    const renderTitle = useMemo(() => {
        if (type === "custom") return title;
        return menuSections.find((s) => s.id === type)?.title;
    }, [menuSections, type, title]);

    const themeColor = globalSettings?.themeColor;
    if (!showTitle) return null;

    // Timeline SectionTitle is rendered by the template wrapper (renderTimelineItem)
    // This is a minimal fallback for basic section
    return (
        <div
            data-section-title-spacing={globalSettings?.sectionTitleSpacing != null ? "true" : undefined}
            className="text-xl font-bold mb-4"
            style={{...({ "--section-title-spacing": `${Math.min(60, Math.max(0, globalSettings?.sectionTitleSpacing ?? 16))}px` } as CSSProperties),
                color: themeColor,
                fontSize: `${globalSettings?.headerSize || 20}px`,
            }}
        >
            {renderTitle}
        </div>
    );
};

export default SectionTitle;
