// Migrated from Magic Resume; see THIRD_PARTY_NOTICES.md; adapted for Codekin React.
import React, { useState, useRef, useEffect } from "react";
import { motion } from "framer-motion";
import { LoadingState } from "@/components/LoadingState";
import { Upload, X } from "lucide-react";
import { useTranslations } from "@/i18n/compat/client";
import { toast } from "sonner";
import { useResumePhotoUpload } from "@/features/resume/useResumePhotoUpload";
import { useAuthStore } from "@/features/auth/session";
import { errorMessage } from "@/lib/errors";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
  DrawerFooter,
  DrawerClose,
} from "@/components/ui/drawer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  PhotoConfig,
  DEFAULT_CONFIG,
  getRatioMultiplier,
  getBorderRadiusValue,
} from "@/types/resume";
import { Textarea } from "@/components/ui/textarea";
import { useResumeStore, useResumeEditorContext } from "@/store/useResumeStore";
import { cn } from "@/lib/utils";

const DEFAULT_AVATAR = "/resume-avatar.png";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  photo?: string;
  config?: PhotoConfig;
  onPhotoChange: (photo: string | undefined, config?: PhotoConfig, fileId?: string | null) => void;
  onConfigChange: (config: PhotoConfig) => void;
}

const PhotoConfigDrawer: React.FC<Props> = ({
  isOpen,
  onClose,
  photo,
  config: initialConfig,
  onPhotoChange,
  onConfigChange,
  ...props
}) => {
  const t = useTranslations("photoConfig");
  const { activeResume, updateBasicInfo } = useResumeStore();
  const { store } = useResumeEditorContext();
  const photoLink = photo && /^https?:\/\//i.test(photo) ? photo : "";
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | undefined>(photo);
  const [isDragging, setIsDragging] = useState(false);
  const [imageUrl, setImageUrl] = useState(photoLink);
  const drawerContentRef = useRef<HTMLDivElement>(null);
  const [config, setConfig] = useState<PhotoConfig>(
    initialConfig || DEFAULT_CONFIG
  );
  const [isMobile, setIsMobile] = useState(false);
  const { userId, policy, upload, phase, progress, isUploading } = useResumePhotoUpload(isOpen);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 768);
    };

    handleResize();
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  useEffect(() => {
    if (isOpen) {
      setConfig(initialConfig || DEFAULT_CONFIG);
      setPreviewUrl(photo === "" ? "" : photo || DEFAULT_AVATAR);
      setImageUrl(photoLink);
    }

  }, [isOpen, initialConfig, photo]);



  const handleFile = async (file: File) => {
    const resumeId = activeResume?.id;
    const owner = userId;
    try {
      const imageData = await upload(file);
      if (!imageData || store.getState().activeResumeId !== resumeId || useAuthStore.getState().userId !== owner) return;
      setPreviewUrl(imageData.preview);
      setImageUrl("");
      updateBasicInfo({
        photo: imageData.preview,
        photoFileId: imageData.fileId,
      });
      toast.success("证件照上传完成");
    } catch (error) {
      toast.error(errorMessage(error, "证件照上传失败，请稍后重试"));
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      void handleFile(file);
    }
    event.target.value = "";
  };

  const handleUrlChange = async (e: string) => {
    if (isUploading) return;
    const resumeId = activeResume?.id;
    const url = e.trim();
    setImageUrl(url);
    if (!url) {
      handleRemovePhoto();
      return;
    }

    try {
      const parsed = new URL(url);
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Invalid image URL');
      const proxyUrl = parsed.href;

      const img = new Image();


      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          reject(new Error(t("upload.timeout")));
        }, 10000);

        img.onload = () => {
          clearTimeout(timer);
          resolve(undefined);
        };
        img.onerror = () => {
          clearTimeout(timer);
          reject(new Error(t("upload.loadError")));
        };
        img.src = proxyUrl;
      });

      if (store.getState().activeResumeId !== resumeId) return;
      setPreviewUrl(proxyUrl);
      updateBasicInfo({
        photo: url,
        photoFileId: null,
      });
      onPhotoChange(url, config, null);
    } catch (error) {
      if (store.getState().activeResumeId !== resumeId) return;
      toast.error(
        t("upload.invalidUrl", {
          defaultMessage: "图片链接无效或无法访问，请尝试使用其他图片链接",
        })
      );
      handleRemovePhoto();
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) {
      handleFile(file);
    }
  };

  const handleRemovePhoto = () => {
    if (isUploading) return;
    setPreviewUrl("");
    setImageUrl("");
    if (inputRef.current) {
      inputRef.current.value = "";
    }

    updateBasicInfo({
      photo: "",
      photoFileId: null,
    });

    onPhotoChange("", config, null);

    setTimeout(() => {
      setPreviewUrl("");
    }, 0);
  };

  const handleConfigChange = (updates: Partial<PhotoConfig>) => {
    const newConfig = { ...config, ...updates };

    if (config.aspectRatio !== "custom") {
      if ("width" in updates) {
        const ratio = getRatioMultiplier(config.aspectRatio);
        newConfig.height =
          Math.round(updates.width! * ratio) > 200
            ? 200
            : Math.round(updates.width! * ratio);
      }
      if ("height" in updates) {
        const ratio = 1 / getRatioMultiplier(config.aspectRatio);
        newConfig.width =
          Math.round(updates.height! * ratio) > 200
            ? 200
            : Math.round(updates.height! * ratio);
      }
    }

    setConfig(newConfig);
    onConfigChange(newConfig);
  };

  const handleInputChange = (
    e: React.ChangeEvent<HTMLInputElement>,
    key: "width" | "height" | "customBorderRadius"
  ) => {
    const value = Number(e.target.value) > 200 ? 200 : e.target.value;

    if (value === "") {
      setConfig((prev) => ({ ...prev, [key]: "" }));
      return;
    }

    const numValue = Number(value);
    if (!isNaN(numValue)) {
      setConfig((prev) => ({ ...prev, [key]: numValue }));
    }
  };

  const handleInputBlur = (
    e: React.FocusEvent<HTMLInputElement>,
    key: "width" | "height" | "customBorderRadius"
  ) => {
    const value = e.target.value;
    const numValue = value === "" ? 0 : Number(value);

    if (key === "customBorderRadius") {
      const maxRadius = Math.min(config.width, config.height) / 2;
      const validValue = Math.max(0, Math.min(numValue, maxRadius));
      handleConfigChange({ customBorderRadius: validValue });
    } else {
      const validValue = Math.max(24, Math.min(numValue, 200));
      handleConfigChange({ [key]: validValue });
    }
  };

  const handleSave = () => {
    if (isUploading) return;
    onPhotoChange(previewUrl, config, activeResume?.basic.photoFileId ?? null);
    onClose();
  };
  return (
    <Drawer
      direction={isMobile ? "bottom" : "left"}
      modal={true}
      open={isOpen}
      dismissible={!isUploading}
      onOpenChange={(open) => !open && !isUploading && onClose()}
    >
      <DrawerContent
        aria-describedby={undefined}
        ref={drawerContentRef}
        className={cn(
          "dark:bg-neutral-900 dark:text-white bg-white",
          "md:fixed md:border-none md:flex md:bottom-0 md:left-0 md:right-0 md:h-[93%] md:max-w-[360px] md:mx-[-1px] md:outline-hidden shadow shadow-blue-500/40"
        )}
      >
        <div className="mx-auto max-h-[90dvh] w-full max-w-md overflow-y-auto">
          <DrawerHeader>
            <DrawerTitle className="text-center">{t("title")}</DrawerTitle>
          </DrawerHeader>
          <div
            className={cn(
              "relative overflow-hidden border-2 transition-all mx-auto",
              isDragging ? "border-blue-500 border-solid" : "border-dashed",
              "dark:border-neutral-700 dark:hover:border-neutral-600 border-neutral-300 hover:border-neutral-400"
            )}
            style={{
              width: `${config.width}px`,
              height: `${config.height}px`,
              borderRadius: getBorderRadiusValue(config),
              maxWidth: "100%",
            }}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            {previewUrl && previewUrl !== "" ? (
              <div className="relative h-full group">
                <img
                  src={previewUrl}
                  alt="Profile"
                  className="w-full h-full object-cover"
                />
                <div
                  className={cn(
                    "absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity",
                    "group-hover:opacity-100"
                  )}
                >
                  <Button
                    onClick={handleRemovePhoto}
                    disabled={isUploading}
                    aria-label="移除简历照片"
                    className="p-1.5 rounded-full bg-white/10 hover:bg-white/20"
                  >
                    <X className="w-4 h-4 text-white" />
                  </Button>
                </div>
              </div>
            ) : (
              <Button
                onClick={() => inputRef.current?.click()}
                disabled={isUploading || !policy.data}
                variant="ghost"
                className="w-full h-full flex flex-col items-center justify-center p-0"
              >
                <Upload
                  className={cn(
                    "w-6 h-6 mb-2",
                    "dark:text-neutral-400 text-neutral-500"
                  )}
                />
              </Button>
            )}
            <motion.input
              ref={inputRef}
              type="file"
              aria-label="简历证件照文件"
              accept={policy.data?.extensions.map(extension => `.${extension}`).join(",")}
              disabled={isUploading || !policy.data}
              onChange={handleFileChange}
              className="hidden"
            />
          </div>
          <div className="p-6 space-y-3">
            <Button className="w-full" variant="outline" disabled={isUploading || !policy.data} onClick={() => inputRef.current?.click()}>上传本地图片</Button>
            {!userId ? <p className="text-sm text-muted-foreground">请登录后上传证件照</p> : policy.data ? <p className="text-xs text-muted-foreground">{t("upload.dragHint")} · {policy.data.extensions.map(extension => extension.toUpperCase()).join(" / ")}，最大 {policy.data.maxBytes / 1024 / 1024} MiB</p> : policy.isFetching ? <LoadingState variant="inline"/> : <><p role="status" className="text-sm text-muted-foreground">{errorMessage(policy.error, "获取上传限制失败")}</p><Button variant="outline" onClick={() => { void policy.refetch(); }}>重新获取上传限制</Button></>}
            {isUploading && <div role="status" className="space-y-2 text-sm"><p>{phase} {progress}%</p><progress aria-label="证件照上传进度" value={progress} max={100} className="w-full"/></div>}
          </div>
          <div className="p-6 space-y-6">
            <div className="space-y-3">
              <h3 className="text-sm font-medium">{t("upload.title")}</h3>
              <Textarea
                disabled={isUploading}
                value={imageUrl}
                aria-label="简历照片链接"
                onChange={e => setImageUrl(e.target.value)}
                onBlur={e => { if (e.target.value !== photoLink) void handleUrlChange(e.target.value) }}
                placeholder={t("upload.urlPlaceholder")}
                className={cn(
                  "h-9",
                  "dark:bg-neutral-800 dark:border-neutral-700"
                )}
              />
            </div>

            <div className="space-y-4">
              <div className="space-y-3">
                <h3 className="text-sm font-medium">{t("config.size")}</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div className="relative">
                    <Input
                      aria-label="照片宽度"
                      inputMode="numeric"
                      value={config.width}
                      onChange={(e) => handleInputChange(e, "width")}
                      onBlur={(e) => handleInputBlur(e, "width")}
                      className={cn(
                        "h-9 pr-7",
                        "dark:bg-neutral-800 dark:border-neutral-700"
                      )}
                      min={24}
                      max={200}
                      placeholder={t("config.widthPlaceholder")}
                    />
                    <div
                      className={cn(
                        "absolute right-3 top-1/2 -translate-y-1/2 text-sm",
                        "dark:text-neutral-400 text-neutral-500"
                      )}
                    >
                      W
                    </div>
                  </div>
                  <div className="relative">
                    <Input
                      aria-label="照片高度"
                      inputMode="numeric"
                      value={config.height}
                      onChange={(e) => handleInputChange(e, "height")}
                      onBlur={(e) => handleInputBlur(e, "height")}
                      className={cn(
                        "h-9 pr-7",
                        "dark:bg-neutral-800 dark:border-neutral-700"
                      )}
                      min={24}
                      max={200}
                      placeholder={t("config.heightPlaceholder")}
                    />
                    <div
                      className={cn(
                        "absolute right-3 top-1/2 -translate-y-1/2 text-sm",
                        "dark:text-neutral-400 text-neutral-500"
                      )}
                    >
                      H
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                <h3 className="text-sm font-medium">
                  {t("config.aspectRatio")}
                </h3>
                <div className="flex flex-wrap gap-2">
                  {(["1:1", "4:3", "3:4", "16:9", "custom"] as const).map(
                    (ratio) => (
                      <Button
                        key={ratio}
                        size="sm"
                        variant={
                          config.aspectRatio === ratio ? "default" : "outline"
                        }
                        onClick={() => {
                          if (ratio !== "custom") {
                            const height = Math.round(
                              config.width * getRatioMultiplier(ratio)
                            );
                            handleConfigChange({ aspectRatio: ratio, height });
                          } else {
                            handleConfigChange({ aspectRatio: ratio });
                          }
                        }}
                      >
                        {ratio === "custom" ? t("config.ratios.custom") : ratio}
                      </Button>
                    )
                  )}
                </div>
              </div>

              <div className="space-y-3">
                <h3 className="text-sm font-medium">
                  {t("config.border-radius")}
                </h3>
                <div className="flex flex-wrap gap-2">
                  {(["none", "medium", "full", "custom"] as const).map(
                    (radius) => (
                      <Button
                        key={radius}
                        size="sm"
                        variant={
                          config.borderRadius === radius ? "default" : "outline"
                        }
                        onClick={() =>
                          handleConfigChange({ borderRadius: radius })
                        }
                      >
                        {radius === "none"
                          ? t("config.borderRadius.none")
                          : radius === "medium"
                          ? t("config.borderRadius.medium")
                          : radius === "full"
                          ? t("config.borderRadius.full")
                          : t("config.borderRadius.custom")}
                      </Button>
                    )
                  )}
                  {config.borderRadius === "custom" && (
                    <Input
                      type="number"
                      value={config.customBorderRadius}
                      onChange={(e) =>
                        handleInputChange(e, "customBorderRadius")
                      }
                      onBlur={(e) => handleInputBlur(e, "customBorderRadius")}
                      className={cn("h-9 mt-2", "dark:bg-neutral-800")}
                      min={0}
                      max={Math.min(config.width, config.height) / 2}
                      placeholder={t("config.borderRadius.customPlaceholder")}
                    />
                  )}
                </div>
              </div>
            </div>
          </div>

          <DrawerFooter>
            <div className="flex gap-2">
              <DrawerClose asChild>
                <Button
                  className="w-full"
                  disabled={isUploading}
                  onClick={handleSave}
                  variant="destructive"
                >
                  {t("actions.close")}
                </Button>
              </DrawerClose>
            </div>
          </DrawerFooter>
        </div>
      </DrawerContent>
    </Drawer>
  );
};

export default PhotoConfigDrawer;
