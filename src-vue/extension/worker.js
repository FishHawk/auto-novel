const notify = (e) => alert(e.message || e);

function isUrlFromMySite(url) {
  return url.includes('fishhawk.top') || url.includes('localhost');
}

function shouldHandleRequest(d) {
  const urlSrc = d.initiator ?? d.originUrl;
  const urlDst = d.url;
  if (!urlSrc || !urlDst) return false;
  const fromMySite = isUrlFromMySite(urlSrc);
  const requestMySite = isUrlFromMySite(urlDst) || urlDst.includes('google');
  return fromMySite && !requestMySite;
}

const v2 = {};

v2.headersReceived = (d) => {
  if (!shouldHandleRequest(d)) {
    return;
  }
  const { responseHeaders } = d;
  for (const c of v2.headersReceived.methods) {
    c(d);
  }
  return { responseHeaders };
};
v2.headersReceived.methods = [];

v2.beforeSendHeaders = (d) => {
  if (!shouldHandleRequest(d)) {
    return;
  }
  const { requestHeaders } = d;
  for (const c of v2.beforeSendHeaders.methods) {
    c(d);
  }
  return { requestHeaders };
};
v2.beforeSendHeaders.methods = [];

// Access-Control-Allow-Headers for OPTIONS
{
  const cache = {};

  v2.beforeSendHeaders.methods.push((d) => {
    if (d.method === 'OPTIONS') {
      const r = d.requestHeaders.find(
        ({ name }) => name.toLowerCase() === 'access-control-request-headers'
      );

      if (r) {
        cache[d.requestId] = r.value;
      }
    }
  });
  v2.headersReceived.methods.push((d) => {
    if (d.method === 'OPTIONS' && cache[d.requestId]) {
      d.responseHeaders.push({
        name: 'Access-Control-Allow-Headers',
        value: cache[d.requestId],
      });
      delete cache[d.requestId];
    }
  });
}

// Access-Control-Allow-Origin
{
  const redirects = {};
  chrome.tabs.onRemoved.addListener((tabId) => delete redirects[tabId]);

  v2.headersReceived.methods.push((d) => {
    if (d.type !== 'main_frame') {
      const { initiator, originUrl, responseHeaders } = d;
      let origin = '*';

      if (!redirects[d.tabId] || !redirects[d.tabId][d.requestId]) {
        try {
          const o = new URL(initiator || originUrl);
          origin = o.origin;
        } catch (e) {}
      }

      if (d.statusCode === 301 || d.statusCode === 302) {
        redirects[d.tabId] = redirects[d.tabId] || {};
        redirects[d.tabId][d.requestId] = true;
      }

      const r = responseHeaders.find(
        ({ name }) => name.toLowerCase() === 'access-control-allow-origin'
      );

      if (r) {
        if (r.value !== '*') {
          r.value = origin;
        }
      } else {
        responseHeaders.push({
          name: 'Access-Control-Allow-Origin',
          value: origin,
        });
      }
    }
  });
}

// Referrer and Origin
{
  v2.beforeSendHeaders.methods.push((d) => {
    try {
      const o = new URL(d.url);
      d.requestHeaders.push(
        {
          name: 'referer',
          value: d.url,
        },
        {
          name: 'origin',
          value: o.origin,
        }
      );
    } catch (e) {}
  });
}

function parseSetCookie(setCookie) {
  const cookie = {};

  const parts = setCookie
    .split(';')
    .filter((str) => typeof str === 'string' && !!str.trim());

  {
    var list = parts.shift().split('=');
    if (list.length > 1) {
      cookie.name = list.shift();
      cookie.value = list.join('=');
    } else {
      cookie.name = '';
      cookie.value = pair;
    }
  }

  parts.forEach(function (part) {
    var list = part.split('=');
    var key = list.shift().trimLeft().toLowerCase();
    var value = list.join('=');
    if (key === 'expires') {
      cookie.expires = new Date(value).getTime();
    } else if (key === 'max-age') {
      cookie.maxAge = parseInt(value, 10);
    } else if (key === 'secure') {
      cookie.secure = true;
    } else if (key === 'httponly') {
      cookie.httpOnly = true;
    } else if (key === 'samesite') {
      cookie.sameSite = value;
    } else {
      cookie[key] = value;
    }
  });

  return cookie;
}

v2.headersReceived.methods.push((d) => {
  if (d.url === 'https://fanyi.baidu.com/v2transapi') {
    return; // 奇怪，不知道为什么得去掉这个url，不然百度翻译概率失败
  }
  for (const header of d.responseHeaders) {
    if (header.name === 'Set-Cookie') {
      const cookie = parseSetCookie(header.value);
      chrome.cookies.set({
        domain: cookie.domain,
        expirationDate: cookie.expires,
        httpOnly: cookie.httpOnly,
        name: cookie.name,
        path: cookie.path,
        sameSite: 'no_restriction',
        secure: true,
        url: d.url,
        value: cookie.value,
      });
    }
  }
});

function start() {
  const isChrome = chrome.runtime.getURL('').startsWith('chrome-extension://');
  const extraOptions = isChrome ? ['extraHeaders'] : [];

  chrome.webRequest.onHeadersReceived.removeListener(v2.headersReceived);
  chrome.webRequest.onHeadersReceived.addListener(
    v2.headersReceived,
    { urls: ['<all_urls>'] },
    ['blocking', 'responseHeaders'].concat(extraOptions)
  );

  chrome.webRequest.onBeforeSendHeaders.removeListener(v2.beforeSendHeaders);
  chrome.webRequest.onBeforeSendHeaders.addListener(
    v2.beforeSendHeaders,
    { urls: ['<all_urls>'] },
    ['blocking', 'requestHeaders'].concat(extraOptions)
  );

  const rules = {
    removeRuleIds: [1, 2],
    addRules: [
      {
        id: 1,
        priority: 1,
        action: {
          type: 'modifyHeaders',
          responseHeaders: [
            {
              operation: 'set',
              header: 'Access-Control-Allow-Methods',
              value: '*',
            },
          ],
        },
        condition: {},
      },
      {
        id: 2,
        priority: 1,
        action: {
          type: 'modifyHeaders',
          responseHeaders: [
            {
              operation: 'set',
              header: 'Allow',
              value: '*',
            },
          ],
        },
        condition: {
          requestMethods: ['options'],
        },
      },
    ],
  };
  chrome.declarativeNetRequest.updateDynamicRules(rules);
}

chrome.runtime.onStartup.addListener(start);
chrome.runtime.onInstalled.addListener(start);

{
  const once = chrome.contextMenus.create({
    title: '启动调试器以支持GPT',
    contexts: ['browser_action'],
    id: 'status-code-enable',
  });
  chrome.runtime.onStartup.addListener(once);
  chrome.runtime.onInstalled.addListener(once);
}

const debug = async (source, method, params) => {
  if (method === 'Fetch.requestPaused') {
    const opts = {
      requestId: params.requestId,
    };
    const status = params.responseStatusCode;
    if (status && status >= 400 && status < 500) {
      const methods = [
        'GET',
        'POST',
        'PUT',
        'OPTIONS',
        'PATCH',
        'PROPFIND',
        'PROPPATCH',
      ];
      const method = params.request?.method;
      if (method && methods.includes(method)) {
        opts.responseCode = 200;
        opts.responseHeaders = params.responseHeaders || [];
      }
    }

    chrome.debugger.sendCommand(
      { tabId: source.tabId },
      'Fetch.continueResponse',
      opts
    );
  }
};

chrome.contextMenus.onClicked.addListener(({ menuItemId, checked }, tab) => {
  if (menuItemId === 'status-code-enable') {
    chrome.debugger.onEvent.removeListener(debug);
    chrome.debugger.onEvent.addListener(debug);

    const target = { tabId: tab.id };

    chrome.debugger.attach(target, '1.2', () => {
      const { lastError } = chrome.runtime;
      if (lastError) {
        console.warn(lastError);
        notify(lastError.message);
      } else {
        chrome.debugger.sendCommand(target, 'Fetch.enable', {
          patterns: [{ requestStage: 'Response' }],
        });
      }
    });
  }
});

chrome.browserAction.onClicked.addListener(() => {
  chrome.tabs.query({ active: true, lastFocusedWindow: true }, (tabs) => {
    const url = tabs[0].url;

    const providers = {
      kakuyomu: (url) => /kakuyomu\.jp\/works\/([0-9]+)/.exec(url)?.[1],
      syosetu: (url) =>
        /syosetu\.com\/([A-Za-z0-9]+)/.exec(url)?.[1].toLowerCase(),
      novelup: (url) => /novelup\.plus\/story\/([0-9]+)/.exec(url)?.[1],
      hameln: (url) => /syosetu\.org\/novel\/([0-9]+)/.exec(url)?.[1],
      pixiv: (url) => {
        let novelId = /pixiv\.net\/novel\/series\/([0-9]+)/.exec(url)?.[1];
        if (novelId === undefined) {
          novelId = /pixiv\.net\/novel\/show.php\?id=([0-9]+)/.exec(url)?.[1];
          if (novelId !== undefined) {
            novelId = 's' + novelId;
          }
        }
        return novelId;
      },
      alphapolis: (url) => {
        const matched =
          /www\.alphapolis\.co\.jp\/novel\/([0-9]+)\/([0-9]+)/.exec(url);
        if (matched) {
          return `${matched[1]}-${matched[2]}`;
        } else {
          return undefined;
        }
      },
      novelism: (url) => /novelism\.jp\/novel\/([^\/]+)/.exec(url)?.[1],
    };
    for (const providerId in providers) {
      const provider = providers[providerId];
      const novelId = provider(url);
      if (novelId !== undefined) {
        chrome.tabs.create({
          url: `https://books.fishhawk.top/novel/${providerId}/${novelId}`,
        });
      }
    }
  });
});
