
# serve-debug（本地 Web-UI 调试）

> [!IMPORTANT]
> `serve-debug` 是仓库内的独立本地辅助工具目录，不属于 Android `:app` 模块，不参与 APK 构建，也不属于 Hook / RPC / 任务调度主执行链。
> 它只用于在桌面环境下复用模块内的 Web 资源与本地导出数据，进行页面预览、抓包辅助与配置排查。

本目录用于 **Sesame-AG** 的本地调试/抓包辅助脚本与 Web-UI 预览，默认与 Android 主模块分开理解、分开维护。

目标：
- 在不依赖 Android / Xposed 运行环境的情况下，快速预览与调试模块内的 `assets/web` 页面；
- 在本地通过 `config.json` + `friend.json` 模拟运行时数据，便于排查 UI/配置问题。

## 依赖

- Python `>= 3.12`
- 推荐使用 `uv`（也可使用 `pip`，但本项目已提供 `uv.lock`）

## 快速开始（推荐：uv）

在仓库根目录执行：

1. 准备 Web 资源与配置文件
   - 将模块的 `app/src/main/assets/web` 目录拷贝到本目录，重命名为 `serve-debug/web`
   - 将模块运行产生/导出的 `config.json` 拷贝到 `serve-debug/`
   - 将任意一个用户配置的 `friend.json` 拷贝到 `serve-debug/`

2. 启动 Web-UI 调试服务
   - `cd serve-debug`
   - `uv sync`
   - `uv run webui.py`

3. 访问页面
   - `http://127.0.0.1:8080/index.html`
   - `http://127.0.0.1:8080/semi_index.html`
   - `http://127.0.0.1:8080/varlet_index.html`

## 运行 main.py（可选）

- `cd serve-debug`
- `uv run main.py`

## 说明与安全提示

- 本目录用于本地调试，**不会内置任何 Token / Key**；如需第三方服务，请自行在配置文件中填写。
- 若你开启了 Hook 数据转发/抓包相关功能，请确保目标地址为你信任的本地服务。


