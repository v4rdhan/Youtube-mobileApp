/* Channel presentation uses the site's own results, account menu and actions. */
(function () {
  if (window !== window.top) return;
  if (window.streamShieldUpdateChannels) {
    window.streamShieldUpdateChannels();
    return;
  }

  const channelFilter = 'EgIQAg==';
  let frame = 0;
  let page;
  let back;
  let directory;
  let biography;
  let lastQuery;

  function makeIcon(pathData) {
    const namespace = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(namespace, 'svg');
    const attributes = {
      viewBox: '0 0 24 24', width: 24, height: 24, fill: 'none',
      stroke: 'currentColor', 'stroke-width': 1.6, 'stroke-linecap': 'round',
      'stroke-linejoin': 'round', 'aria-hidden': 'true'
    };
    for (const [name, value] of Object.entries(attributes)) svg.setAttribute(name, value);
    const path = document.createElementNS(namespace, 'path');
    path.setAttribute('d', pathData);
    svg.append(path);
    return svg;
  }

  function makeBack() {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'ss-channel-back';
    button.setAttribute('aria-label', 'Back');
    button.append(makeIcon('m14 7-5 5 5 5'));
    button.addEventListener('click', () => {
      if (window.navigation?.canGoBack ?? (history.length > 1)) history.back();
      else location.replace(page === 'detail'
        ? '/results?search_query=creators&sp=' + encodeURIComponent(channelFilter)
        : '/');
    });
    return button;
  }

  function makeDirectory() {
    const wrapper = document.createElement('div');
    wrapper.className = 'ss-channel-directory';
    const title = document.createElement('h1');
    title.textContent = 'Channels';
    const form = document.createElement('form');
    form.action = '/results';
    form.method = 'get';
    form.setAttribute('role', 'search');
    const button = document.createElement('button');
    button.type = 'submit';
    button.setAttribute('aria-label', 'Submit channel search');
    button.append(makeIcon('M17.5 10.5a7 7 0 1 1-14 0 7 7 0 0 1 14 0ZM16 16l4 4'));
    const input = document.createElement('input');
    input.type = 'search';
    input.name = 'search_query';
    input.placeholder = 'Search channels';
    input.setAttribute('aria-label', input.placeholder);
    input.enterKeyHint = 'search';
    input.required = true;
    const filter = document.createElement('input');
    filter.type = 'hidden';
    filter.name = 'sp';
    filter.value = channelFilter;
    form.append(button, input, filter);
    wrapper.append(title, form);
    form.addEventListener('submit', (event) => {
      input.value = input.value.trim();
      if (!input.value) {
        event.preventDefault();
        input.focus();
      }
    });
    return wrapper;
  }

  function makeBiography() {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'ss-channel-biography';
    button.append('Biography', makeIcon('m8 10 4 4 4-4'));
    button.addEventListener('click', () => {
      button.parentElement.querySelector('.ytTruncatedTextAbsoluteButton')?.click();
    });
    return button;
  }

  function update() {
    frame = 0;
    const url = new URL(location.href);
    const filter = url.searchParams.get('sp');
    const isDirectory = url.pathname === '/results' &&
      (filter === channelFilter || filter === encodeURIComponent(channelFilter));
    const isDetail = /^\/(?:@[^/]+|(?:channel|c|user)\/[^/]+)(?:\/|$)/.test(url.pathname);
    const nextPage = isDirectory ? 'directory' : isDetail ? 'detail' : null;
    if (page !== nextPage) lastQuery = undefined;
    page = nextPage;
    const root = document.documentElement;
    if (!root) return;
    if (root.dataset.ssChannelPage !== page) {
      if (page) root.dataset.ssChannelPage = page;
      else delete root.dataset.ssChannelPage;
    }
    if (!isDirectory) directory?.remove();
    if (!isDetail) biography?.remove();
    if (!page) {
      back?.remove();
      return;
    }

    const header = document.querySelector('ytm-mobile-topbar-renderer > .mobile-topbar-header');
    if (header) {
      back ??= makeBack();
      if (back.parentElement !== header) header.prepend(back);
      if (isDirectory && header.querySelector('.search-bar')) {
        directory ??= makeDirectory();
        if (directory.parentElement !== header) back.after(directory);
        const query = url.searchParams.get('search_query') ?? '';
        if (query !== lastQuery) {
          directory.querySelector('input[type="search"]').value = query;
          lastQuery = query;
        }
      }
    }

    if (isDirectory) {
      const filterGroup = document.querySelector('ytm-search ytm-search-filter-group-renderer[data-has-options="true"]');
      const select = filterGroup?.querySelector('select');
      if (select && !filterGroup.querySelector('.ss-channel-filter-label')) {
        const label = document.createElement('label');
        label.className = 'ss-channel-filter-label';
        label.textContent = 'Filter by';
        select.id ||= 'ss-channel-filter';
        label.htmlFor = select.id;
        filterGroup.prepend(label);
      }
    }

    if (isDetail) {
      const description = document.querySelector('ytm-browse .ytPageHeaderViewModelDescription');
      if (description?.querySelector('.ytTruncatedTextAbsoluteButton')) {
        const actions = description.parentElement;
        biography ??= makeBiography();
        if (biography.parentElement !== actions) actions.append(biography);
      }
    }
  }

  function scheduleUpdate() {
    if (!frame) frame = requestAnimationFrame(update);
  }

  window.streamShieldUpdateChannels = update;
  update();
  new MutationObserver(() => {
    if (page) scheduleUpdate();
  }).observe(document, { childList: true, subtree: true });
  window.addEventListener('popstate', scheduleUpdate);
  document.addEventListener('yt-navigate-finish', scheduleUpdate);
})();
