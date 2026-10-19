import type { CSSProperties } from "react";
import { useMemo } from "react";
import { GlobalSettings } from "@/types/resume";
import { useTemplateContext } from "../../TemplateContext";
import { cn } from "@/lib/utils";

interface SectionTitleProps {
    globalSettings?: GlobalSettings;
    type: string;
    title?: string;
    showTitle?: boolean;
    variant?: "default" | "sidebar";
}

const SectionTitle = ({ type, title, globalSettings, showTitle = true, variant = "default" }: SectionTitleProps) => {
    const templateContext = useTemplateContext();
    const menuSections = templateContext?.menuSections ?? [];

    const renderTitle = useMemo(() => {
        if (type === "custom") return title;
        return menuSections.find((s) => s.id === type)?.title;
    }, [menuSections, type, title]);

    const themeColor = globalSettings?.themeColor;
    if (!showTitle) return null;

    const isSidebar = variant === "sidebar";

    return (
        <h3
            data-section-title-spacing={globalSettings?.sectionTitleSpacing != null ? "true" : undefined}
            className={cn(
                "pb-1 font-semibold mb-2 uppercase tracking-wider",
                isSidebar ? "border-b border-white/20" : "border-b"
            )}
            style={{...({ "--section-title-spacing": `${Math.min(60, Math.max(0, globalSettings?.sectionTitleSpacing ?? 16))}px` } as CSSProperties),
                fontSize: `${isSidebar ? (globalSettings?.headerSize || 18) - 2 : (globalSettings?.headerSize || 18)}px`,
                fontWeight: "bold",
                color: isSidebar ? "#ffffff" : themeColor,
                borderColor: isSidebar ? "rgba(255,255,255,0.2)" : themeColor,
                marginBottom: isSidebar ? "12px" : `${globalSettings?.paragraphSpacing}px`,
            }}
        >
            {renderTitle}
        </h3>
    );
};

export default SectionTitle;
