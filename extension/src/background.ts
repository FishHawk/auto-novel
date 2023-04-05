chrome.webRequest.onBeforeSendHeaders.addListener(
  function (details) {
    console.log(1);
    const customOrigin = details.requestHeaders?.find(
      (it) => it.name === 'customOrigin'
    )?.value;
    console.log(customOrigin);

    for (var i = 0; i < details.requestHeaders!!.length; ++i) {
      if (details.requestHeaders!![i].name === 'Origin')
        details.requestHeaders!![i].value = 'https://fanyi.baidu.com';
    }
    return {
      requestHeaders: details.requestHeaders,
    };
  },
  {
    urls: ['<all_urls>'],
  },
  ['blocking', 'requestHeaders', 'extraHeaders']
);
chrome.webRequest.onHeadersReceived.addListener(
  function (details) {
    console.log(2);
  },
  {
    urls: ['<all_urls>'],
  },
  ['blocking', 'responseHeaders', 'extraHeaders']
);

export {};
