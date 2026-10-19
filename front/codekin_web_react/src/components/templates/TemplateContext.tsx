import React, { createContext, useContext } from "react";
import { MenuSection } from "@/types/resume";

interface TemplateContextProps {
  templateId: string;
  menuSections: MenuSection[];
  onSectionSelect?: (id: string) => void;
}

const TemplateContext = createContext<TemplateContextProps | undefined>(undefined);

export const TemplateProvider: React.FC<{
  templateId: string;
  menuSections: MenuSection[];
  onSectionSelect?: (id: string) => void;
  children: React.ReactNode;
}> = ({ templateId, menuSections, children, onSectionSelect }) => {
  return (
    <TemplateContext.Provider value={{ templateId, menuSections, onSectionSelect }}>
      {children}
    </TemplateContext.Provider>
  );
};

export const useTemplateContext = () => {
  return useContext(TemplateContext);
};
