/**
 * 步骤 2/3：采集 biliboard.uk 全站曲库（约 14155 首），用于 App 的离线搜索
 *
 * 用法：node tools/2-harvest-catalog.cjs
 * 输出：<工作区>/data/biliboard-catalog.json
 *
 * 接口：/api/public/songs?page=N&per_page=100  →  { data, total, page, per_page }
 *       每条自带 rank_history，即该曲的历史上榜记录。
 */
const fs = require('fs');
const path = require('path');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
const BASE = 'https://biliboard.uk';
const PROJECT_ROOT = path.join(__dirname, '..');
const WORKSPACE_ROOT = path.join(PROJECT_ROOT, '..');
const OUT_DIR = path.join(WORKSPACE_ROOT, 'data');
const OUT = path.join(OUT_DIR, 'biliboard-catalog.json');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(pathname, tries = 4) {
  let lastErr;
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(BASE + pathname, {
        headers: { 'User-Agent': UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(30000),
      });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return await r.json();
    } catch (e) {
      lastErr = e;
      await sleep(400 * (i + 1));
    }
  }
  throw new Error(`${pathname} 失败: ${lastErr && lastErr.message}`);
}

(async () => {
  const t0 = Date.now();
  const perPage = 100;
  const first = await api(`/api/public/songs?page=1&per_page=${perPage}`);
  const total = first.total;
  const pages = Math.ceil(total / perPage);
  console.log(`曲库共 ${total} 首，每页 ${perPage}，需 ${pages} 页`);

  const all = [...first.data];
  const seen = new Set(all.map((s) => s.bvid));
  let dup = 0;

  let cursor = 2;
  await Promise.all(
    Array.from({ length: 6 }, async () => {
      while (true) {
        const p = cursor++;
        if (p > pages) return;
        const j = await api(`/api/public/songs?page=${p}&per_page=${perPage}`);
        for (const s of j.data) {
          if (seen.has(s.bvid)) { dup++; continue; }
          seen.add(s.bvid);
          all.push(s);
        }
        if (p % 20 === 0) console.log(`  已抓 ${p}/${pages} 页，累计 ${all.length} 首`);
      }
    })
  );

  const credit = (c) => ({ n: c.name, u: c.wiki_url || c.moegirl_url || null });
  const hist = (h) => ({ b: h.board_id, i: h.issue_id, y: h.issue_year, w: h.issue_week, r: h.rank });
  const songs = all.map((s) => ({
    bvid: s.bvid,
    title: s.title,
    titleCn: s.title_cn || undefined,
    pubtime: s.pubtime,
    firstRecordedAt: s.first_recorded_at || undefined,
    producers: (s.producers || []).map(credit),
    vocalists: (s.vocalists || []).map(credit),
    history: (s.rank_history || []).map(hist),
  }));

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({
    generatedAt: new Date().toISOString(),
    source: BASE,
    api: '/api/public/songs?page=&per_page=',
    total,
    count: songs.length,
    songs,
  }));
  console.log(`\n写出 ${OUT} (${(fs.statSync(OUT).size / 1048576).toFixed(2)} MB)`);
  console.log(`去重后 ${songs.length} 首（重复 ${dup}）| 有上榜记录 ${songs.filter((s) => s.history.length).length} 首`);
  console.log(`耗时 ${((Date.now() - t0) / 1000).toFixed(1)}s`);
})().catch((e) => {
  console.error('采集失败', e);
  process.exit(1);
});
