const ids = {
  urlTemplate: document.getElementById("urlTemplate"),
  method: document.getElementById("method"),
  currentPage: document.getElementById("currentPage"),
  maxPages: document.getElementById("maxPages"),
  headers: document.getElementById("headers"),
  body: document.getElementById("body"),
  dataPath: document.getElementById("dataPath"),
  totalPagesPath: document.getElementById("totalPagesPath"),
  status: document.getElementById("status"),
  fetchCurrent: document.getElementById("fetchCurrent"),
  fetchAll: document.getElementById("fetchAll"),
  stop: document.getElementById("stop"),
  export: document.getElementById("export")
};

function parseJSONOrEmpty(text, fieldName) {
  const trimmed = text.trim();
  if (!trimmed) {
    return {};
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    throw new Error(`${fieldName} 不是合法 JSON`);
  }
}

function parseHeaders(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    return {};
  }

  if (trimmed.startsWith("{")) {
    return parseJSONOrEmpty(trimmed, "请求头");
  }

  const headers = {};
  const lines = trimmed.split(/\r?\n/);
  for (const line of lines) {
    const cleanLine = line.trim();
    if (!cleanLine) {
      continue;
    }
    const sepIndex = cleanLine.indexOf(":");
    if (sepIndex <= 0) {
      throw new Error(`请求头格式错误: ${cleanLine}`);
    }
    const key = cleanLine.slice(0, sepIndex).trim();
    const value = cleanLine.slice(sepIndex + 1).trim();
    if (!key) {
      throw new Error(`请求头键为空: ${cleanLine}`);
    }
    headers[key] = value;
  }

  return headers;
}

async function getActiveTabId() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || typeof tab.id !== "number") {
    throw new Error("无法获取当前标签页");
  }
  return tab.id;
}

function collectConfig() {
  const urlTemplate = ids.urlTemplate.value.trim();
  const dataPath = ids.dataPath.value.trim();

  if (!urlTemplate) {
    throw new Error("请填写接口 URL 模板");
  }
  if (!dataPath) {
    throw new Error("请填写数据路径");
  }

  return {
    urlTemplate,
    method: ids.method.value,
    currentPage: Number(ids.currentPage.value || 1),
    maxPages: Number(ids.maxPages.value || 200),
    headers: parseHeaders(ids.headers.value),
    bodyTemplate: ids.body.value.trim(),
    dataPath,
    totalPagesPath: ids.totalPagesPath.value.trim()
  };
}

async function send(action, payload = {}) {
  return chrome.runtime.sendMessage({ action, ...payload });
}

function renderStatus(state) {
  if (!state) {
    ids.status.textContent = "暂无任务";
    return;
  }
  const lines = [
    `运行中: ${state.running ? "是" : "否"}`,
    `模式: ${state.mode || "-"}`,
    `已抓页数: ${state.fetchedPages || 0}`,
    `下一页: ${state.nextPage || "-"}`,
    `总页数: ${state.totalPages || "未知"}`,
    `数据条数: ${(state.results || []).length}`,
    `错误数: ${(state.errors || []).length}`
  ];

  if (state.lastMessage) {
    lines.push(`消息: ${state.lastMessage}`);
  }
  ids.status.textContent = lines.join("\n");
}

async function refreshState() {
  const resp = await send("getState");
  if (resp?.ok) {
    renderStatus(resp.state);
    if (resp.state?.config) {
      const c = resp.state.config;
      ids.urlTemplate.value = c.urlTemplate || "";
      ids.method.value = c.method || "GET";
      ids.currentPage.value = c.currentPage || 1;
      ids.maxPages.value = c.maxPages || 200;
      ids.headers.value = JSON.stringify(c.headers || {}, null, 2);
      ids.body.value = c.bodyTemplate || "";
      ids.dataPath.value = c.dataPath || "";
      ids.totalPagesPath.value = c.totalPagesPath || "";
    }
  }
}

ids.fetchCurrent.addEventListener("click", async () => {
  try {
    const tabId = await getActiveTabId();
    const config = collectConfig();
    const resp = await send("fetchCurrentPage", { tabId, config });
    if (!resp?.ok) {
      throw new Error(resp?.error || "当前页抓取失败");
    }
    await refreshState();
  } catch (error) {
    ids.status.textContent = String(error.message || error);
  }
});

ids.fetchAll.addEventListener("click", async () => {
  try {
    const tabId = await getActiveTabId();
    const config = collectConfig();
    const resp = await send("startFetchAllPages", { tabId, config });
    if (!resp?.ok) {
      throw new Error(resp?.error || "启动失败");
    }
    await refreshState();
  } catch (error) {
    ids.status.textContent = String(error.message || error);
  }
});

ids.stop.addEventListener("click", async () => {
  const resp = await send("stopFetch");
  if (!resp?.ok) {
    ids.status.textContent = resp?.error || "停止失败";
  }
  await refreshState();
});

ids.export.addEventListener("click", async () => {
  const resp = await send("exportJson");
  if (!resp?.ok) {
    ids.status.textContent = resp?.error || "导出失败";
    return;
  }
  await refreshState();
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.action === "stateUpdated") {
    renderStatus(message.state);
  }
});

refreshState();
