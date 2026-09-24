(() => {
  const texts = [], links = [], images = [];
  let remaining = 2000000;
  const add = value => {
    if (typeof value !== 'string' || !value || remaining <= 0) return;
    const text = value.slice(0, remaining);
    texts.push(text); remaining -= text.length;
  };
  add(location.href);
  // Structured blocks first so an article's surrounding prose cannot corrupt a PGN.
  document.querySelectorAll('pre,textarea,code,[data-pgn],[data-fen],input,script').forEach(el => {
    add(el.value || el.textContent);
    add(el.getAttribute('data-pgn')); add(el.getAttribute('data-fen'));
  });
  const visit = doc => {
    add(doc.body?.innerText);
    Array.from(doc.querySelectorAll('*')).slice(0, 30000).forEach(el => {
      for (const attr of el.attributes) {
        if (/fen|pgn|position|href|src|alt|title/i.test(attr.name)) add(attr.value);
      }
    });
    doc.querySelectorAll('a[href],iframe[src]').forEach(el => {
      const url = el.href || el.src;
      if (url?.startsWith('https:') && links.length < 300) links.push(url);
    });
    doc.querySelectorAll('img').forEach(el => {
      const width = el.naturalWidth || el.width, height = el.naturalHeight || el.height;
      if ((width >= 120 && height >= 120) || /chess|board|diagram/i.test(el.alt + el.src)) {
        images.push({url: el.currentSrc || el.src || el.getAttribute('data-src'), label: el.alt || 'Page image', area: width * height});
      }
    });
    doc.querySelectorAll('canvas,svg').forEach(el => {
      const box = el.getBoundingClientRect();
      if (box.width < 120 || box.height < 120) return;
      try {
        const url = el.tagName.toLowerCase() === 'canvas' ? el.toDataURL('image/png')
          : 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(new XMLSerializer().serializeToString(el))));
        if (url.length < 4000000) images.push({url, label: 'Rendered board', area: box.width * box.height});
      } catch (_) { /* Cross-origin canvas: the viewport scan can still read it. */ }
    });
    doc.querySelectorAll('iframe').forEach(frame => {
      try { if (frame.contentDocument?.body) visit(frame.contentDocument); } catch (_) {}
    });
  };
  visit(document);
  images.sort((a,b) => b.area - a.area);
  return JSON.stringify({texts, links: [...new Set(links)], images: images.slice(0, 20),
    limited: remaining <= 0 || images.length > 20});
})()
