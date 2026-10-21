# 背景记录

App 内置背景由 `app/src/main/java/com/local/weekday/BackdropView.java` 使用 Android Canvas 在运行时绘制，原工程没有可直接复制的 PNG/JPG 原图。下面的 SVG 是用于归档和查看的静态记录图，不参与 APK 运行；实际显示仍支持亮度、模糊和遮罩设置。

| 编号 | 名称 | 类型 | 主色 |
| ---: | --- | --- | --- |
| 0 | 晨光 | 原有 | `#F4DFCB` → `#F9F1DF` |
| 1 | 晴空 | 原有 | `#CFE7EE` → `#F4F5EE` |
| 2 | 森林 | 原有 | `#B5CBB8` → `#E4EADB` |
| 3 | 星夜 | 原有 | `#253F59` → `#627D94` |
| 4 | 山岚 | 原有 | `#C9D4D0` → `#EDF0E5` |
| 5 | 纸页 | 原有 | `#F3F1E8` → `#FAF7ED` |
| 6 | 春日 | 新增季节 | `#D8EAD2` → `#FFE9DF` |
| 7 | 夏日 | 新增季节 | `#A9DCE5` → `#EDF7D9` |
| 8 | 秋色 | 新增季节 | `#D4935F` → `#F2DFB5` |
| 9 | 冬霜 | 新增季节 | `#B9D3E5` → `#F0F6FB` |

记录图：

- [晨光](01-morning.svg)
- [晴空](02-sky.svg)
- [森林](03-forest.svg)
- [星夜](04-night.svg)
- [山岚](05-mountain.svg)
- [纸页](06-paper.svg)
- [春日](07-spring.svg)
- [夏日](08-summer.svg)
- [秋色](09-autumn.svg)
- [冬霜](10-winter.svg)
