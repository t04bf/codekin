import { CustomFieldType } from "@/types/resume";
import { getProjectLinkHref } from "@/lib/projectLink";

const trim = (value?: string) => value?.trim() || "";

export const getCustomFieldDisplayText = (
  field: Pick<CustomFieldType, "label" | "value" | "displayLabel">
) => {
  const label = trim(field.label);
  const value = trim(field.value);

  if (field.displayLabel) {
    return label && value ? `${label}: ${value}` : label || value;
  }

  return value;
};

export const shouldShowCustomFieldLabelPrefix = (
  field: Pick<CustomFieldType, "label" | "displayLabel">
) => {
  return !field.displayLabel && Boolean(trim(field.label));
};

export const getCustomFieldHref = (
  field: Pick<CustomFieldType, "value" | "valueType">
) => {
  if (field.valueType !== "link") return null;

  return getProjectLinkHref(field.value);
};
