<div align="center">
  <img src="artwork/sesame-ag-app-icon.svg" width="140" alt="Sesame-AG Logo" />
  <h1>Sesame-AG</h1>
  <p><strong>面向学习研究场景的 Alipay LSPosed 模块分支。</strong></p>
  <p>项目技术源流可追溯至 <code>XQuickEnergy</code> / <code>Sesame</code> 系列，当前以 <code>Sesame-AG</code> 名义继续维护、重构与补全。</p>
</div>

---

## 项目说明

`Sesame-AG` 是一个以学习、研究、静态分析、代码整理和调试为主要目的的 Android 模块仓库。

它不是单一上游仓库的线性延续，而是在 `XQuickEnergy`、`Sesame`、`Sesame-TK` 等公开维护线的基础上，结合当前仓库自己的结构调整、功能补全、调试能力和工程化改造，继续独立演进的分支。

> [!IMPORTANT]
> 为避免滥用，确保模块以学习研究为主的定位，当前仓库仅对以下环境组合提供“支持维护”：
>
> - `LSPosed API 101+`
> - `已 Root`
> - `Android 16+`
>
> 上述范围之外的框架版本、系统版本、运行条件或仅能在非上述环境中复现的问题，可能仍会被讨论，但不属于维护者承诺持续适配、优先处理或长期回归验证的范围。

请先确认你已经接受本仓库 [LEGAL.md](LEGAL.md) 中整理的许可说明、使用边界、风险提示与免责声明。

当你下载、克隆、编译、安装、运行、修改、分发或以其他方式使用本项目时，即视为你已经阅读、理解并接受 [LICENSE](LICENSE) 与本仓库 [LEGAL.md](LEGAL.md) 中的相关说明。

---

## 研究参考与技术源流

> [!NOTE]
> 下面只保留与当前仓库关系最直接的公开维护线索，用于帮助理解代码来源，不表示单一的法律继承关系。

| 时期 | 代表仓库 | 与当前仓库的关系 |
| --- | --- | --- |
| 早期起点 | [yongjun925/autocollectenergy](https://github.com/yongjun925/autocollectenergy) | 更上游的历史根系。 |
| `XQuickEnergy` 公开线 | [pansong291/XQuickEnergy](https://github.com/pansong291/XQuickEnergy) | 早期公开实现思路的重要节点。 |
| `XQuickEnergy` 扩展线 | [constanline/XQuickEnergy](https://github.com/constanline/XQuickEnergy) | 后续 `Sesame` 系列的重要工程前置。 |
| `Sesame` 主续线 | [LazyImmortal/Sesame](https://github.com/LazyImmortal/Sesame) | 公开续线中的关键节点。 |
| `Sesame-TK` 阶段 | [TKaxv-7S/Sesame-TK](https://github.com/TKaxv-7S/Sesame-TK) | `Sesame` 之后的重要接棒节点。 |
| `Sesame-TK` 分叉传播线 | [Liujishou/Sesame-TK-Fork](https://github.com/Liujishou/Sesame-TK-Fork) / [Fansirsqi/Sesame-TK](https://github.com/Fansirsqi/Sesame-TK) | 与当前代码组织更接近的一条传播线。 |
| 并行参考线 | [Youyu-Github/Sesame-TK-Y](https://github.com/Youyu-Github/Sesame-TK-Y) / [iceshell/Sesame-TK3](https://github.com/iceshell/Sesame-TK3) | 提供并行参考，但不是唯一上游。 |
| 当前仓库 | `Sesame-AG` | 综合吸收多条公开实现思路后继续独立演进。 |

如果你关注的是合规、许可证或附加限制，请不要只依据“历史源流”做判断，而应结合提交时期、来源仓库声明和当前仓库的 [LEGAL.md](LEGAL.md) 一并理解。

---

## 开源协议

### 协议说明

> [!NOTE]
> 由于历史代码跨越多个维护阶段，协议更适合按时间切分理解：

| 时间范围 / 版本节点 | 适用说明 |
| --- | --- |
| **2024 年 7 月 15 日之前** 的历史代码 | 按上游公开说明，主要落在 `Apache-2.0` 语境。 |
| **2024 年 7 月 15 日** 起的后续代码 | 仓库历史说明曾表述为 `GPLv3`，并附带“禁止商业用途、禁止二次修改后闭源发布”的项目声明。 |
| **2025 年 7 月 23 日** 起至 **2025 年 12 月 15 日** 之间的本仓库历史说明 | 仓库声明曾切换到 [WTFPL](https://www.wtfpl.net/)。 |
| 2025 年 12 月 15 日-`3871-0.0.3` 版本 | 项目开源协议为 `GPLv3`，并继续附带相同的项目声明。 |
| `3871-0.0.3` 版本起 | 项目开源协议调整为 `AGPL-3.0`。 |

### 相关文档

- [LEGAL.md](LEGAL.md)：适用说明、非代码资产边界、风险提示与免责声明。
- [CONTRIBUTING.md](CONTRIBUTING.md)：Issue / PR 规则与协作边界。

---

## 贡献者

感谢所有为 `Sesame-AG` 提交代码、修复问题、补全文档与改进协作流程的贡献者。

[![贡献者头像墙](https://contrib.rocks/image?repo=aoguai/Sesame-AG&max=200)](https://github.com/aoguai/Sesame-AG/graphs/contributors)

