# 后生架构

## 界面实现

组件在 `ProductUi.kt`，颜色在 `colors.xml`。界面规则见 [DESIGN.md](DESIGN.md)。

图标为 Material Symbols Rounded 转成的 VectorDrawable，由 `scripts/material-icon.sh` 生成，不引入图标或动画依赖。

页面按系统栏与刘海 Insets 留白，长内容纵向滚动，行只设最小高度。应用列表由原生 ListView 回收行，避免一次创建 500 个控件。
