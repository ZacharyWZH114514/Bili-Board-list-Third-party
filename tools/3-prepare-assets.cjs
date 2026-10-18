/**
 * 步骤 3/3：把采集到的原始数据精简成 App 内置资源
 *
 * 用法：node tools/3-prepare-assets.cjs
 * 输入：<工作区>/data/biliboard-snapshot.json, <工作区>/data/biliboard-catalog.json
 * 输出：app/src/main/assets/biliboard_snapshot.json, app/src/main/assets/catalog.json
 *
 * 精简策略：
 *   - 榜单：去掉可由 bvid 推导的 coverUrl，以及 rateHistory / isAnnual / specialStatus 等展示用不到的字段
 *   - 曲库：改用短键名，只保留检索所需字段，并附一张「别名 -> 正名」表
 */
const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.join(__dirname, '..');
const WORKSPACE_ROOT = path.join(PROJECT_ROOT, '..');
const DATA = path.join(WORKSPACE_ROOT, 'data');
const ASSETS = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets');

const SNAPSHOT_SRC = path.join(DATA, 'biliboard-snapshot.json');
const CATALOG_SRC = path.join(DATA, 'biliboard-catalog.json');
const SNAPSHOT_OUT = path.join(ASSETS, 'biliboard_snapshot.json');
const CATALOG_OUT = path.join(ASSETS, 'catalog.json');

const mb = (p) => (fs.statSync(p).size / 1048576).toFixed(2);

function prepareSnapshot() {
  const snap = JSON.parse(fs.readFileSync(SNAPSHOT_SRC, 'utf8'));

  const credit = (c) => ({
    n: c.name,
    u: c.url || c.wiki_url || c.moegirl_url || null,
    a: c.aliases && c.aliases.length ? c.aliases : undefined,
  });

  const entry = (e) => ({
    rank: e.rank,
    title: e.title,
    titleCn: e.titleCn || undefined,
    producers: (e.producers || []).map(credit),
    vocalists: (e.vocalists || []).map(credit),
    bvid: e.bvid,
    pubtime: e.pubtime,
    firstRecordedAt: e.firstRecordedAt || undefined,
    views: e.stats ? e.stats.views : 0,
    likes: e.stats ? e.stats.likes : 0,
    coins: e.stats ? e.stats.coins : 0,
    favorites: e.stats ? e.stats.favorites : 0,
    score: e.score,
    lastRank: e.lastRank === null || e.lastRank === undefined ? undefined : e.lastRank,
    weeksOnBoard: e.weeksOnBoard,
    peakRank: e.peakRank,
    rate: e.rate && e.rate !== '--%' ? e.rate : undefined,
    rankChangeKind: e.rankChange ? e.rankChange.kind : undefined,
    rankChangeDiff: e.rankChange && e.rankChange.diff ? e.rankChange.diff : undefined,
  });

  const out = {
    generatedAt: snap.generatedAt,
    source: snap.source,
    boards: snap.boards.map((b) => ({
      id: b.id,
      name: b.name,
      issues: b.issues.map((i) => ({
        issueId: i.issue_id,
        year: i.year,
        week: i.week,
        videoCount: i.video_count,
        endDate: i.end_date,
        videoBvid: i.video_bvid || undefined,
        videoPubdate: i.video_pubdate || undefined,
        rankings: (i.rankings || []).map(entry),
      })),
    })),
  };

  fs.mkdirSync(ASSETS, { recursive: true });
  fs.writeFileSync(SNAPSHOT_OUT, JSON.stringify(out));

  let issues = 0, songs = 0;
  const bvids = new Set();
  for (const b of out.boards) {
    issues += b.issues.length;
    for (const i of b.issues) {
      songs += i.rankings.length;
      i.rankings.forEach((r) => bvids.add(r.bvid));
    }
  }
  console.log(`榜单 ${mb(SNAPSHOT_SRC)} MB -> ${mb(SNAPSHOT_OUT)} MB | ${out.boards.length} 个榜 / ${issues} 期 / ${songs} 条排名 / ${bvids.size} 首唯一曲`);
}

function prepareCatalog() {
  const catalog = JSON.parse(fs.readFileSync(CATALOG_SRC, 'utf8'));
  const snap = JSON.parse(fs.readFileSync(SNAPSHOT_SRC, 'utf8'));

  // 别名表：别名(小写) -> 正名
  const aliasMap = new Map();
  const addAlias = (canonical, alias) => {
    if (!alias || !canonical) return;
    const k = String(alias).trim().toLowerCase();
    if (!k || k === String(canonical).trim().toLowerCase()) return;
    if (!aliasMap.has(k)) aliasMap.set(k, canonical);
  };
  for (const b of snap.boards) {
    for (const i of b.issues) {
      for (const e of i.rankings || []) {
        for (const c of [...(e.producers || []), ...(e.vocalists || [])]) {
          for (const a of c.aliases || []) addAlias(c.name, a);
        }
      }
    }
  }

  const songs = catalog.songs.map((s) => {
    const o = { b: s.bvid, t: s.title };
    if (s.titleCn) o.c = s.titleCn;
    const p = (s.producers || []).map((x) => x.n).filter(Boolean);
    if (p.length) o.p = p;
    const v = (s.vocalists || []).map((x) => x.n).filter(Boolean);
    if (v.length) o.v = v;
    if (s.pubtime) o.u = s.pubtime;
    const h = (s.history || []).map((x) => [x.b, x.i, x.r]);
    if (h.length) o.h = h;
    return o;
  });

  fs.mkdirSync(ASSETS, { recursive: true });
  fs.writeFileSync(CATALOG_OUT, JSON.stringify({
    generatedAt: catalog.generatedAt,
    source: catalog.source,
    api: catalog.api,
    total: catalog.total,
    count: songs.length,
    aliases: Object.fromEntries(aliasMap),
    songs,
  }));

  console.log(`曲库 ${mb(CATALOG_SRC)} MB -> ${mb(CATALOG_OUT)} MB | ${songs.length} 首 / 别名 ${aliasMap.size} 条`);
}

prepareSnapshot();
prepareCatalog();
console.log('\n资源已更新，重新构建 APK 即可生效。');
