# Codekin 品牌资源

## 单独的吉祥物图片

`mascot/` 下为五张独立透明 PNG，1254 × 1254：

- `hero.png`：主形象，伸手邀请。
- `hello.png`：招呼。
- `focus.png`：坐着读书。
- `keep-going.png`：加油。
- `celebrate.png`：庆祝。

保留已确认动图的柔和立体插画质感；这些是栅格插画，不是可编辑的 3D 模型。由内置 image_gen 生成，各姿势仍可能有轻微细节差异。生成提示词保存在 `prompts.json`。

## 独立动图

- `animation/jump-wave.webp`：透明背景，适合网站。
- `animation/jump-wave.gif`：白底播放预览。
- `animation/frames/`：12 张完整角色 PNG。
- `animation/animation.json`：制作时的帧时长和配准记录。

直接保存先前认可的动画，未重新生成动作。GIF 编码包含额外的末帧停留记录，因此与 WebP 的编码帧数不同。

## Logo 与字标

- `brand/logo-icon.png`：主品牌 Logo，绿色圆润 C + 右上角橙色折角，从之前的 C 设计原稿提取，透明 PNG。
- `brand/wordmark.png`：codekin 字标，与主 Logo 搭配使用。
- `brand/mascot-avatar.png`：橙色吉祥物头像衍生图标，不作为主品牌 Logo。

以上是位图提取稿，不是矢量源文件。网站品牌入口使用 C Logo，吉祥物头像用于人物或活动场景。

## 原始高清设计图

`reference/codekin-brand-board-original.png` 是之前确认的完整品牌设计图，1536 × 1024，直接恢复原始生成文件，未通过放大截图制作。对应衣服内使用绿色 c Logo 的版本。当前五张立体姿势图仍在 `mascot/`，此图作为原版品牌设计参考保留。

原始设计图下方的橙色头像属于吉祥物衍生图标；当前主 Logo 以 `brand/logo-icon.png` 的 C 标识为准。
