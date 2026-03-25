const STATE_KEY = "crawlerState";
const ALARM_NAME = "crawler-next-page";

let state = {
  running: false,
  mode: null,
  tabId: null,
  config: null,
  nextPage: null,
  totalPages: null,
  fetchedPages: 0,
  errors: [],
  results: [],
  lastMessage: ""
};

function cloneState() {
  return JSON.parse(JSON.stringify(state));
}

async function persistState() {
  await chrome.storage.local.set({ [STATE_KEY]: state });
  await chrome.runtime.sendMessage({ action: "stateUpdated", state: cloneState() }).catch(() => undefined);
}

function replacePageToken(value, page) {
  return String(value).replaceAll("{page}", String(page));
}

function buildRequest(config, page) {
  const url = replacePageToken(config.urlTemplate, page);
  const options = {
    method: config.method || "GET",
    headers: config.headers || {}
  };

  if (options.method !== "GET" && config.bodyTemplate) {
    options.body = replacePageToken(config.bodyTemplate, page);
  }

  return { url, options };
}

function getByPath(obj, path) {
  if (!path) {
    return obj;
  }
  return path.split(".").reduce((acc, key) => {
    if (acc === null || acc === undefined) {
      return undefined;
    }
    const normalized = /^\d+$/.test(key) ? Number(key) : key;
    return acc[normalized];
  }, obj);
}

function toArray(value) {
  if (Array.isArray(value)) {
    return value;
  }
  if (value === undefined || value === null) {
    return [];
  }
  return [value];
}

function complete(message) {
  state.running = false;
  state.lastMessage = message;
  chrome.alarms.clear(ALARM_NAME);
}

function fail(message, error) {
  const detail = error ? `${message}: ${String(error.message || error)}` : message;
  state.errors.push(detail);
  state.lastMessage = detail;
}

async function executeFetchInTab(tabId, request) {
  const [{ result }] = await chrome.scripting.executeScript({
    target: { tabId },
    world: "MAIN",
    func: async ({ url, options }) => {
      const response = await fetch(url, options);
      const text = await response.text();
      return {
        ok: response.ok,
        status: response.status,
        body: text
      };
    },
    args: [request]
  });

  if (!result) {
    throw new Error("页面未返回结果");
  }
  if (!result.ok) {
    throw new Error(`HTTP ${result.status}`);
  }

  try {
    return JSON.parse(result.body);
  } catch {
    throw new Error("接口响应不是 JSON");
  }
}

async function processSinglePage(page) {
  const config = state.config;
  const request = buildRequest(config, page);
  const json = await executeFetchInTab(state.tabId, request);

  const pageData = toArray(getByPath(json, config.dataPath));
  state.results.push(...pageData);
  state.fetchedPages += 1;

  if (!state.totalPages && config.totalPagesPath) {
    const tp = Number(getByPath(json, config.totalPagesPath));
    if (Number.isFinite(tp) && tp > 0) {
      state.totalPages = tp;
    }
  }

  return pageData.length;
}

async function processNextPage() {
  if (!state.running || !state.config) {
    return;
  }

  try {
    const tab = await chrome.tabs.get(state.tabId);
    if (!tab) {
      complete("标签页不可用，任务已停止");
      await persistState();
      return;
    }
  } catch {
    complete("标签页已关闭，任务已停止");
    await persistState();
    return;
  }

  const page = state.nextPage;

  if (state.totalPages && page > state.totalPages) {
    complete(`抓取完成，共 ${state.results.length} 条`);
    await persistState();
    return;
  }

  if (page > state.config.maxPages) {
    complete(`达到最大页数限制 ${state.config.maxPages}，已停止`);
    await persistState();
    return;
  }

  try {
    const count = await processSinglePage(page);
    state.lastMessage = `已抓取第 ${page} 页，新增 ${count} 条`;
    state.nextPage += 1;

    if (!state.totalPages && !state.config.totalPagesPath && count === 0) {
      complete(`抓取完成，共 ${state.results.length} 条`);
      await persistState();
      return;
    }
  } catch (error) {
    fail(`第 ${page} 页抓取失败`, error);
    state.nextPage += 1;
  }

  await persistState();

  if (state.running) {
    await chrome.alarms.create(ALARM_NAME, { when: Date.now() + 300 });
  }
}

async function loadState() {
  const stored = await chrome.storage.local.get(STATE_KEY);
  if (stored[STATE_KEY]) {
    state = { ...state, ...stored[STATE_KEY] };
  }
}

chrome.runtime.onInstalled.addListener(async () => {
  await loadState();
  await persistState();
});

chrome.runtime.onStartup.addListener(async () => {
  await loadState();
  if (state.running) {
    await chrome.alarms.create(ALARM_NAME, { when: Date.now() + 300 });
  }
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name !== ALARM_NAME) {
    return;
  }
  await processNextPage();
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    if (message.action === "getState") {
      await loadState();
      sendResponse({ ok: true, state: cloneState() });
      return;
    }

    if (message.action === "fetchCurrentPage") {
      const { tabId, config } = message;
      state = {
        running: false,
        mode: "current",
        tabId,
        config,
        nextPage: config.currentPage,
        totalPages: null,
        fetchedPages: 0,
        errors: [],
        results: [],
        lastMessage: "开始抓取当前页"
      };

      try {
        await processSinglePage(config.currentPage);
        state.lastMessage = `当前页抓取完成，共 ${state.results.length} 条`;
      } catch (error) {
        fail("当前页抓取失败", error);
      }

      await persistState();
      sendResponse({ ok: true, state: cloneState() });
      return;
    }

    if (message.action === "startFetchAllPages") {
      const { tabId, config } = message;
      state = {
        running: true,
        mode: "all",
        tabId,
        config,
        nextPage: config.currentPage,
        totalPages: null,
        fetchedPages: 0,
        errors: [],
        results: [],
        lastMessage: "全部页抓取已启动"
      };

      await persistState();
      await chrome.alarms.create(ALARM_NAME, { when: Date.now() + 100 });
      sendResponse({ ok: true, state: cloneState() });
      return;
    }

    if (message.action === "stopFetch") {
      complete("任务已手动停止");
      await persistState();
      sendResponse({ ok: true, state: cloneState() });
      return;
    }

    if (message.action === "exportJson") {
      const filename = `api-data-${Date.now()}.json`;
      const jsonText = JSON.stringify(state.results || [], null, 2);
      const dataUrl = `data:application/json;charset=utf-8,${encodeURIComponent(jsonText)}`;

      try {
        const downloadId = await chrome.downloads.download({
          url: dataUrl,
          filename,
          saveAs: true,
          conflictAction: "uniquify"
        });

        if (typeof downloadId !== "number") {
          throw new Error("浏览器未返回下载任务 ID");
        }

        state.lastMessage = `已导出 ${filename}`;
        await persistState();
        sendResponse({ ok: true, state: cloneState() });
      } catch (error) {
        fail("导出失败", error);
        await persistState();
        sendResponse({ ok: false, error: String(error.message || error) });
      }
      return;
    }

    sendResponse({ ok: false, error: "未知操作" });
  })().catch(async (error) => {
    fail("后台处理异常", error);
    await persistState();
    sendResponse({ ok: false, error: String(error.message || error) });
  });

  return true;
});
