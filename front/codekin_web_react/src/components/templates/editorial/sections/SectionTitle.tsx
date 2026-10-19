import type { CSSProperties } from "react";
import { useMemo } from "react";
import { GlobalSettings } from "@/types/resume";
import { useTemplateContext } from "../../TemplateContext";

interface SectionTitleProps {
  type: string;
  title?: string;
  globalSettings?: GlobalSettings;
  showTitle?: boolean;
}

const SectionTitle = ({ type, title, globalSettings, showTitle = true }: SectionTitleProps) => {
  const templateContext = useTemplateContext();
  const menuSections = templateContext?.menuSections ?? [];

  const renderTitle = useMemo(() => {
    if (type === "custom") return title;
    return menuSections.find((s) => s.id === type)?.title;
  }, [menuSections, type, title]);

  if (!showTitle) return null;

  return (
    <div
            data-section-title-spacing={globalSettings?.sectionTitleSpacing != null ? "true" : undefined} className="w-full shrink-0 mb-6" style={{ ...({ "--section-title-spacing": `${Math.min(60, Math.max(0, globalSettings?.sectionTitleSpacing ?? 16))}px` } as CSSProperties), }}>
      <h3
        className="font-bold uppercase tracking-[0.2em]"
        style={{ 
          fontSize: `${globalSettings?.headerSize || 18}px`,
          color: globalSettings?.themeColor || "#8e8e8e",
          marginBottom: `${globalSettings?.paragraphSpacing || 16}px`
        }}
      >
        {renderTitle}
      </h3>
    </div>
  );
};

export default SectionTitle;
