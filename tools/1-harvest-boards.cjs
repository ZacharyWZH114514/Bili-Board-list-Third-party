/**
 * 步骤 1/3：从 biliboard.uk 采集全部榜单数据（周榜 / 传说曲周榜 / 年榜）
 *
 * 用法：node tools/1-harvest-boards.cjs
 * 输出：<工作区>/data/biliboard-snapshot.json
 *
 * 用到的公开接口（均为 GET，返回 JSON）：
 *   /api/public/boards
 *   /api/public/boards/{boardId}/issues
 *   /api/public/boards/{boardId}/issues/{issueId}/rankings
 */
const fs = require('fs');
const path = require('path');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
const BASE = 'https://biliboard.uk';
const PROJECT_ROOT = path.join(__dirname, '..');
const WORKSPACE_ROOT = path.join(PROJECT_ROOT, '..');
const OUT_DIR = path.join(WORKSPACE_ROOT, 'data');
const OUT = path.join(OUT_DIR, 'biliboard-snapshot.json');
const RAW_DIR = path.join(OUT_DIR, 'raw');

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

/** 简单并发池 */
async function pool(items, limit, fn) {
  const results = new Array(items.length);
  let cursor = 0, done = 0;
  const total = items.length;
  await Promise.all(
    Array.from({ length: Math.min(limit, total) }, async () => {
      while (true) {
        const i = cursor++;
        if (i >= total) return;
        results[i] = await fn(items[i], i);
        if (++done % 25 === 0 || done === total) console.log(`  进度 ${done}/${total}`);
      }
    })
  );
  return results;
}

(async () => {
  fs.mkdirSync(RAW_DIR, { recursive: true });
  const t0 = Date.now();

  console.log('拉取榜单目录…');
  const boards = await api('/api/public/boards');
  console.log('榜单:', boards.map((b) => `${b.id}=${b.name}`).join(', '));

  const out = {
    generatedAt: new Date().toISOString(),
    source: BASE,
    api: {
      boards: '/api/public/boards',
      issues: '/api/public/boards/{boardId}/issues',
      rankings: '/api/public/boards/{boardId}/issues/{issueId}/rankings',
      cover: '/api/provider/biliimg/bvid/{bvid}',
    },
    boards: [],
  };

  let logged = false;
  let totalSongs = 0;
  const warnings = [];

  for (const b of boards) {
    console.log(`\n=== board ${b.id} ${b.name} ===`);
    const issues = await api(`/api/public/boards/${b.id}/issues`);
    fs.writeFileSync(path.join(RAW_DIR, `board-${b.id}-issues.json`), JSON.stringify(issues, null, 1));
    console.log(`  ${issues.length} 期`);

    const sorted = [...issues].sort((a, z) => z.issue_id - a.issue_id); // 新 -> 旧
    const rankings = await pool(sorted, 5, async (iss) => {
      const rk = await api(`/api/public/boards/${b.id}/issues/${iss.issue_id}/rankings`);
      if (!logged && rk.length) {
        console.log('\n  [字段样例] keys =', Object.keys(rk[0]).join(', '));
        console.log('  [字段样例] 首条 =', JSON.stringify(rk[0]));
        logged = true;
      }
      return rk;
    });

    const merged = sorted.map((iss, i) => {
      const list = Array.isArray(rankings[i]) ? rankings[i] : [];
      if (iss.video_count && list.length !== iss.video_count) {
        warnings.push(`board${b.id} 第${iss.issue_id}期 video_count=${iss.video_count} 但 rankings=${list.length}`);
      }
      totalSongs += list.length;
      return { ...iss, rankings: list };
    });

    out.boards.push({ id: b.id, name: b.name, issueCount: merged.length, issues: merged });
    console.log(`  完成：${merged.length} 期，累计 ${totalSongs} 首`);
  }

  out.stats = {
    boardCount: out.boards.length,
    issueTotal: out.boards.reduce((a, b) => a + b.issues.length, 0),
    songTotal: totalSongs,
    warnings,
  };

  fs.writeFileSync(OUT, JSON.stringify(out));
  console.log(`\n写出 ${OUT} (${(fs.statSync(OUT).size / 1048576).toFixed(2)} MB)`);
  console.log('统计:', JSON.stringify(out.stats).slice(0, 400));
  console.log(`耗时 ${((Date.now() - t0) / 1000).toFixed(1)}s`);
})().catch((e) => {
  console.error('采集失败', e);
  process.exit(1);
});
