import { motion } from "framer-motion";
import { useTemplateContext } from "../TemplateContext";
import { cn } from "@/lib/utils";

interface SectionWrapperProps {
    sectionId: string;
    children: React.ReactNode;
    className?: string;
    style?: React.CSSProperties;
}

/**
 * Thin interaction wrapper for all section components.
 * Provides hover highlight + click-to-select behavior.
 */
const SectionWrapper: React.FC<SectionWrapperProps> = ({
    sectionId,
    children,
    className = "",
    style,
}) => {
    const context = useTemplateContext();

    return (
        <motion.div
            data-resume-section-id={sectionId}
            className={cn(
                "hover:cursor-pointer rounded-md transition-all duration-300 ease-in-out hover:shadow-md",
                "hover:bg-[#f9f8f3]",
                className
            )}
            style={style}
            onClick={() => context?.onSectionSelect?.(sectionId)}
        >
            {children}
        </motion.div>
    );
};

export default SectionWrapper;
