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

    return (
        <div
            data-section-title-spacing={globalSettings?.sectionTitleSpacing != null ? "true" : undefined} className="relative" style={{ ...({ "--section-title-spacing": `${Math.min(60, Math.max(0, globalSettings?.sectionTitleSpacing ?? 16))}px` } as CSSProperties), }}>
            <div className="absolute inset-0" style={{ backgroundColor: themeColor, opacity: 0.1 }} />
            <h3
                className="pl-4 py-1 flex items-center relative font-bold"
                style={{
                    fontSize: `${globalSettings?.headerSize || 18}px`,
                    color: themeColor,
                    borderLeft: `3px solid ${themeColor}`,
                    marginBottom: `${globalSettings?.paragraphSpacing}px`,
                }}
            >
                {renderTitle}
            </h3>
        </div>
    );
};

export default SectionTitle;
