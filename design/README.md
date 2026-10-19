# Codekin 设计资源

当前资源统一保存在 `codekin/`，不再维护重复的 `resource/codekin/` 副本。

| 目录或文件 | 保留原因 |
| --- | --- |
| `codekin/mascot/` | 已确认立体插画风格的五个独立姿势：主形象、招呼、读书、加油、庆祝；透明 PNG。 |
| `codekin/animation/jump-wave.webp` | 网站使用的透明背景招手动画。 |
| `codekin/animation/jump-wave.gif` | 白底动画预览，方便查看与分享。 |
| `codekin/animation/frames/` | 12 张完整角色帧，后续调整或重新导出动画时需要。 |
| `codekin/animation/animation.json` | 制作时的帧时长与配准记录，不是网站播放器配置。 |
| `codekin/brand/` | 主 Logo 为绿色 C + 橙色折角（`logo-icon.png`）；另保留字标和吉祥物头像（`mascot-avatar.png`）。位图稿，放大印刷前应制作矢量源文件。 |
| `codekin/reference/codekin-brand-board-original.png` | 之前确认的完整品牌设计图，1536 × 1024 原始生成稿，保留主形象、四个动作、Logo、字标和配色。 |
| `codekin/prompts.json` | 五张静态姿势图的生成提示词，便于继续迭代。 |

整理时逐项核对了 `codekin/` 与 `resource/codekin/` 的 24 个文件，SHA-256 完全一致，仅删除重复副本和 `.DS_Store` 元数据。未删除独有素材。

当前目录未包含早期失败动画、2D 探索稿或 Blender 重建稿；不能据此认定这些历史文件在其他位置已经删除。

详细素材说明见 [codekin/README.md](codekin/README.md)。
