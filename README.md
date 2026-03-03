# Paged API JSON Collector (Chrome Extension)

## 功能
- 抓取指定接口的当前页数据。
- 自动翻页抓取全部页数据（支持总页数字段或空数据自动停止）。
- 结果保存为 JSON 数组。
- 一键导出 JSON 文件。
- 弹窗关闭/失焦后，后台继续抓取任务。

## 安装
1. 打开 Chrome `chrome://extensions/`
2. 开启「开发者模式」
3. 点击「加载已解压的扩展程序」
4. 选择本目录：
   - `/Users/yuechunyuechun/Desktop/codex_demo/skills`

## 使用
1. 在目标网站页面打开扩展。
2. 填写：
   - `接口 URL 模板`：例如 `https://example.com/api/list?page={page}`
   - `数据路径`：例如 `data.list`
   - 可选 `总页数字段路径`：例如 `data.totalPages`
3. 点击：
   - `抓取当前页`：仅抓取当前页。
   - `自动抓取全部页`：后台自动抓取所有页。
4. 任务完成后点击 `导出 JSON`。

## 说明
- URL 和请求体中都支持 `{page}` 占位符。
- 请求头支持两种格式：
  - JSON：`{"Content-Type":"application/json"}`
  - 文本逐行：`Content-Type: application/json`
- 如果不填写总页路径，会在某一页数据为空数组时自动停止。
- 「最大页数」用于避免无限翻页。
