package za.bilbd.app.data

/**
 * P主 相关的纯计算。
 *
 * 抽成纯函数是为了能直接单测：这里面有两个容易写错的点——
 * 同一首歌可能有多位 P主（索引里会重复出现），
 * 而用户可能同时关注了这几位（合并流必须去重）。
 */
object ProducerIndex {

    /**
     * 构建 P主 → 曲目 的倒排索引。
     * 每个 P主 名下的曲目已按投稿时间从新到旧排好，并去掉重复。
     */
    fun build(songs: List<CatalogSong>): Map<String, List<CatalogSong>> {
        val index = HashMap<String, MutableList<CatalogSong>>()
        for (song in songs) {
            for (name in song.producers) {
                val key = name.trim()
                if (key.isEmpty()) continue
                index.getOrPut(key) { ArrayList() }.add(song)
            }
        }
        return index.mapValues { (_, list) ->
            // 同一首里同一个 P主 写了两遍时（数据里出现过）也要去重
            list.distinctBy { it.bvid }.sortedByDescending { it.pubtime }
        }
    }

    /**
     * 把多个 P主 的曲目合并成一条流，按投稿时间从新到旧。
     * 一首歌有多个 P主 而用户都关注了时，只出现一次。
     */
    fun mergeFeed(
        index: Map<String, List<CatalogSong>>,
        names: Collection<String>,
    ): List<CatalogSong> {
        if (names.isEmpty()) return emptyList()
        return names.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { index[it].orEmpty().asSequence() }
            .distinctBy { it.bvid }
            .sortedByDescending { it.pubtime }
            .toList()
    }

    /** 投稿最多的 P主，用于「还没有关注」时的推荐；同一数量按名字排序，保证结果稳定 */
    fun topProducers(index: Map<String, List<CatalogSong>>, limit: Int = 12): List<Pair<String, Int>> =
        index.entries
            .map { it.key to it.value.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
}
